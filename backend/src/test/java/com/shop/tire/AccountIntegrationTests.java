package com.shop.tire;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.security.SecureRandom;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AccountIntegrationTests {
    private static final String JWT_SECRET = newSecret();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> JWT_SECRET);
        registry.add("app.tracking.secret", () -> Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(32)));
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired JwtEncoder jwtEncoder;
    @MockitoBean JavaMailSender mail;

    private static final String PASSWORD = "Testing password 123!";
    private static final String NEW_PASSWORD = "Changed password 456!";

    @Test
    void applicationAndDocumentationRemainPublic() throws Exception {
        try (var client = new Client()) {
            assertThat(client.get("/actuator/health").statusCode()).isEqualTo(200);
            assertThat(client.get("/actuator/health").body()).contains("\"status\":\"UP\"");
            var docs = client.get("/v3/api-docs");
            assertThat(docs.statusCode()).isEqualTo(200);
            assertThat(body(docs).path("paths").has("/api/auth/logout")).isTrue();
            assertThat(body(docs).at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
            assertThat(body(docs).path("paths").has("/api/auth/csrf")).isFalse();
            assertThat(body(docs).path("paths").has("/api/hello")).isFalse();
            assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name like 'spring_session%'", Integer.class)).isZero();
            assertThat(client.get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
            assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class)).isEqualTo(11);
        }
    }

    @Test
    void registrationNormalizesEmailAndStoresOnlyPasswordHash() throws Exception {
        try (var client = new Client()) {
            String email = email().toUpperCase(Locale.ROOT);
            var response = client.post("/api/auth/register", registration(email));
            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(body(response).get("role").asText()).isEqualTo("CUSTOMER");
            assertThat(body(response).get("email").asText()).isEqualTo(email.toLowerCase(Locale.ROOT));
            assertThat(response.body()).doesNotContain("password", PASSWORD);
            String hash = jdbc.queryForObject("select password_hash from app_account where email = ?", String.class, email.toLowerCase(Locale.ROOT));
            assertThat(hash).isNotEqualTo(PASSWORD);
            assertThat(encoder.matches(PASSWORD, hash)).isTrue();
            assertThat(client.get("/api/account/me").statusCode()).isEqualTo(401);
            assertThat(client.post("/api/auth/register", registration(email.toLowerCase(Locale.ROOT))).statusCode()).isEqualTo(409);
        }
    }

    @Test
    void rejectsRoleInjectionAndInvalidFieldsWithoutLeakingSecrets() throws Exception {
        try (var client = new Client()) {
            var forged = new HashMap<String, Object>(registration(email()));
            forged.put("role", "ADMIN");
            assertProblem(client.post("/api/auth/register", forged), 400, "INVALID_REQUEST");
            String secret = "short-secret";
            var invalid = client.post("/api/auth/register", Map.of("email", "invalid", "password", secret,
                    "firstName", "", "lastName", "Test"));
            assertProblem(invalid, 400, "VALIDATION_FAILED");
            assertThat(invalid.body()).contains("errors").doesNotContain(secret, "stackTrace");
        }
    }

    @Test
    void loginIssuesJwtWithoutSessionAndLogoutRevokesAllTokens() throws Exception {
        String email = fixture("CUSTOMER");
        try (var client = new Client(); var other = new Client()) {
            assertProblem(client.post("/api/auth/login", Map.of("email", email, "password", "wrong password")),
                    401, "AUTHENTICATION_FAILED");
            var login = client.login(email, PASSWORD);
            assertThat(login.statusCode()).isEqualTo(200);
            assertThat(body(login).get("tokenType").asText()).isEqualTo("Bearer");
            assertThat(body(login).get("expiresIn").asLong()).isEqualTo(900);
            assertThat(login.headers().allValues("set-cookie")).isEmpty();
            var payload = json.readTree(Base64.getUrlDecoder().decode(client.token.split("\\.")[1]));
            assertThat(payload.get("sub").asText()).isEqualTo(id(email).toString());
            assertThat(payload.get("role").asText()).isEqualTo("CUSTOMER");
            assertThat(payload.toString()).doesNotContain("password", "email", "firstName");
            var me = client.get("/api/account/me");
            assertThat(me.statusCode()).isEqualTo(200);
            assertThat(me.headers().allValues("set-cookie")).isEmpty();
            other.login(email, PASSWORD);
            assertThat(other.token).isNotEqualTo(client.token);
            assertThat(client.post("/api/auth/logout", Map.of()).statusCode()).isEqualTo(204);
            assertProblem(client.get("/api/account/me"), 401, "AUTHENTICATION_REQUIRED");
            assertProblem(other.get("/api/account/me"), 401, "AUTHENTICATION_REQUIRED");
            assertThat(client.login(email, PASSWORD).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void profilesAndAddressesBelongOnlyToAuthenticatedOwner() throws Exception {
        String first = fixture("CUSTOMER");
        String second = fixture("CUSTOMER");
        try (var a = new Client(); var b = new Client()) {
            a.login(first, PASSWORD);
            b.login(second, PASSWORD);
            assertThat(a.get("/api/account/me/address").statusCode()).isEqualTo(404);
            assertThat(a.put("/api/account/me/address", address()).statusCode()).isEqualTo(200);
            assertThat(a.get("/api/account/me/address").body()).contains("Warszawa");
            assertThat(b.get("/api/account/me/address").statusCode()).isEqualTo(404);
            UUID other = id(second);
            assertThat(a.put("/api/account/" + other + "/address", address()).statusCode()).isEqualTo(404);
            assertThat(a.put("/api/account/me", Map.of("firstName", "Anna", "lastName", "Nowak", "phone", "123456789")).statusCode()).isEqualTo(200);
            assertThat(body(a.get("/api/account/me")).get("firstName").asText()).isEqualTo("Anna");
            assertThat(body(b.get("/api/account/me")).get("firstName").asText()).isEqualTo("Test");
        }
    }

    @Test
    void customerAndEmployeeCannotPerformAdminActions() throws Exception {
        for (String role : List.of("CUSTOMER", "EMPLOYEE")) {
            try (var client = new Client()) {
                client.login(fixture(role), PASSWORD);
                assertProblem(client.get("/api/admin/accounts?email=bootstrap@example.test"), 403, "ACCESS_DENIED");
                assertProblem(client.post("/api/admin/employees", invitation(email())), 403, "ACCESS_DENIED");
                assertProblem(client.post("/api/admin/accounts/" + UUID.randomUUID() + "/password-reset", Map.of()),
                        403, "ACCESS_DENIED");
            }
        }
        verifyNoInteractions(mail);
    }

    @Test
    void employeeInvitationIsOneTimeAndCannotAuthenticateBeforePasswordIsSet() throws Exception {
        String employee = email();
        try (var admin = new Client(); var user = new Client()) {
            admin.login(fixture("ADMIN"), PASSWORD);
            var invite = admin.post("/api/admin/employees", invitation(employee));
            assertThat(invite.statusCode()).isEqualTo(201);
            assertThat(invite.body()).contains("INVITED").doesNotContain("token", "password");
            assertThat(user.login(employee, PASSWORD).statusCode()).isEqualTo(401);
            String token = sentToken();
            assertThat(jdbc.queryForObject("select token_hash from account_action_token where account_id = ?", String.class, id(employee)))
                    .hasSize(64).isNotEqualTo(token);
            assertThat(user.post("/api/auth/password-actions/complete", completion(token)).statusCode()).isEqualTo(204);
            assertThat(user.post("/api/auth/password-actions/complete", completion(token)).statusCode()).isEqualTo(400);
            assertThat(user.login(employee, NEW_PASSWORD).statusCode()).isEqualTo(200);
            assertThat(body(user.get("/api/account/me")).get("role").asText()).isEqualTo("EMPLOYEE");
        }
    }

    @Test
    void passwordResetInvalidatesAllExistingTokensAndOldPassword() throws Exception {
        String customer = fixture("CUSTOMER");
        try (var admin = new Client(); var old = new Client(); var other = new Client(); var reset = new Client()) {
            admin.login(fixture("ADMIN"), PASSWORD);
            old.login(customer, PASSWORD);
            other.login(customer, PASSWORD);
            assertThat(admin.post("/api/admin/accounts/" + id(customer) + "/password-reset", Map.of()).statusCode()).isEqualTo(204);
            assertThat(reset.post("/api/auth/password-actions/complete", completion(sentToken())).statusCode()).isEqualTo(204);
            assertProblem(old.get("/api/account/me"), 401, "AUTHENTICATION_REQUIRED");
            assertProblem(other.get("/api/account/me"), 401, "AUTHENTICATION_REQUIRED");
            assertThat(reset.login(customer, PASSWORD).statusCode()).isEqualTo(401);
            assertThat(reset.login(customer, NEW_PASSWORD).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void adminCanFindAnAccountForPasswordResetWithoutExposingCredentials() throws Exception {
        String customer = fixture("CUSTOMER");
        try (var admin = new Client()) {
            admin.login(fixture("ADMIN"), PASSWORD);
            var found = admin.get("/api/admin/accounts?email=" + customer.toUpperCase(Locale.ROOT));
            assertThat(found.statusCode()).isEqualTo(200);
            assertThat(body(found).get("id").asText()).isEqualTo(id(customer).toString());
            assertThat(found.body()).doesNotContain("password", "pbkdf2");
            assertProblem(admin.get("/api/admin/accounts?email=" + email()), 404, "ACCOUNT_NOT_FOUND");
            assertThat(admin.get("/api/admin/accounts?email=invalid").statusCode()).isEqualTo(400);
        }
    }

    @Test
    void expiredAndReplacedTokensCannotChangePassword() throws Exception {
        String customer = fixture("CUSTOMER");
        try (var admin = new Client(); var user = new Client()) {
            admin.login(fixture("ADMIN"), PASSWORD);
            String path = "/api/admin/accounts/" + id(customer) + "/password-reset";
            admin.post(path, Map.of());
            String old = sentToken();
            clearInvocations(mail);
            admin.post(path, Map.of());
            String fresh = sentToken();
            assertThat(user.post("/api/auth/password-actions/complete", completion(old)).statusCode()).isEqualTo(400);
            jdbc.update("update account_action_token set created_at = now() - interval '2 hours', expires_at = now() - interval '1 hour' where account_id = ?", id(customer));
            assertThat(user.post("/api/auth/password-actions/complete", completion(fresh)).statusCode()).isEqualTo(400);
            assertThat(user.login(customer, PASSWORD).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void concurrentConsumptionAllowsOnlyOnePasswordChange() throws Exception {
        String customer = fixture("CUSTOMER");
        try (var admin = new Client(); var a = new Client(); var b = new Client()) {
            admin.login(fixture("ADMIN"), PASSWORD);
            admin.post("/api/admin/accounts/" + id(customer) + "/password-reset", Map.of());
            String token = sentToken();
            var first = CompletableFuture.supplyAsync(() -> uncheckedComplete(a, token));
            var second = CompletableFuture.supplyAsync(() -> uncheckedComplete(b, token));
            assertThat(List.of(first.join(), second.join())).containsExactlyInAnyOrder(204, 400);
        }
    }

    @Test
    void failedMailDeliveryRollsBackInvitation() throws Exception {
        doThrow(new MailSendException("SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));
        String employee = email();
        try (var admin = new Client()) {
            admin.login(fixture("ADMIN"), PASSWORD);
            assertProblem(admin.post("/api/admin/employees", invitation(employee)), 503, "MAIL_UNAVAILABLE");
            assertThat(jdbc.queryForObject("select count(*) from app_account where email = ?", Integer.class, employee)).isZero();
        }
    }

    @Test
    void disabledAccountCannotUseExistingToken() throws Exception {
        String customer = fixture("CUSTOMER");
        try (var user = new Client()) {
            user.login(customer, PASSWORD);
            jdbc.update("update app_account set status = 'DISABLED' where email = ?", customer);
            assertProblem(user.get("/api/account/me"), 401, "AUTHENTICATION_REQUIRED");
            assertThat(user.login(customer, PASSWORD).statusCode()).isEqualTo(401);
        }
    }

    private int uncheckedComplete(Client client, String token) {
        try { return client.raw("POST", "/api/auth/password-actions/complete", completion(token)).statusCode(); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }

    @Test
    void bootstrapIsOptInAndDoesNotResetExistingAdmin() throws Exception {
        String email = "bootstrap@example.test";
        String before = jdbc.queryForObject("select password_hash from app_account where email = ?", String.class, email);
        context.getBean("adminBootstrap", org.springframework.boot.ApplicationRunner.class)
                .run(new org.springframework.boot.DefaultApplicationArguments());
        assertThat(jdbc.queryForObject("select password_hash from app_account where email = ?", String.class, email)).isEqualTo(before);
        try (var admin = new Client()) {
            assertThat(admin.login(email, "Bootstrap test password 123!").statusCode()).isEqualTo(200);
            assertThat(body(admin.get("/api/account/me")).get("role").asText()).isEqualTo("ADMIN");
        }
    }

    @Test
    void rejectsExpiredTamperedUnsignedWrongAlgorithmAndInvalidClaims() throws Exception {
        UUID accountId = id(fixture("CUSTOMER"));
        var now = Instant.now();
        var valid = claims(accountId);
        String validToken = sign(valid.build(), MacAlgorithm.HS256);
        String[] parts = validToken.split("\\.");
        String tamperedPayload = new String(Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8)
                .replace("CUSTOMER", "ADMIN");
        String tampered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "." + parts[2];
        String none = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "." + parts[1] + ".";
        var invalid = List.of(
                "not-a-token", tampered, none,
                sign(valid.build(), MacAlgorithm.HS512),
                sign(claims(accountId).issuedAt(now.minusSeconds(100)).expiresAt(now.minusSeconds(1)).build(), MacAlgorithm.HS256),
                sign(claims(accountId).issuer("untrusted").build(), MacAlgorithm.HS256),
                sign(claims(accountId).audience(List.of("another-api")).build(), MacAlgorithm.HS256),
                sign(claims(accountId).subject("invalid-uuid").build(), MacAlgorithm.HS256),
                sign(claims(accountId).claim("authVersion", -1).build(), MacAlgorithm.HS256),
                sign(claims(accountId).claim("role", "ADMIN").build(), MacAlgorithm.HS256),
                sign(claims(accountId).claims(values -> values.remove("exp")).build(), MacAlgorithm.HS256),
                sign(claims(accountId).claims(values -> values.remove("authVersion")).build(), MacAlgorithm.HS256));
        try (var client = new Client()) {
            for (String token : invalid) {
                client.token = token;
                var response = client.get("/api/account/me");
                assertProblem(response, 401, "AUTHENTICATION_REQUIRED");
                assertThat(response.headers().firstValue("www-authenticate")).contains("Bearer");
                assertThat(response.body()).doesNotContain(token);
            }
        }
    }

    @Test
    void acceptsCredentialsOnlyFromBearerHeader() throws Exception {
        try (var client = new Client()) {
            client.login(fixture("CUSTOMER"), PASSWORD);
            String token = client.token;
            client.token = null;
            assertThat(client.get("/api/account/me?access_token=" + token).statusCode()).isEqualTo(401);
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/account/me"))
                    .header("Cookie", "SESSION=old-session; access_token=" + token).GET().build();
            assertThat(client.http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
            assertThat(client.get("/api/account/me").statusCode()).isEqualTo(401);
        }
    }

    @Test
    void corsAllowsOnlyConfiguredAngularOrigin() throws Exception {
        try (var client = new Client()) {
            for (String origin : List.of("http://localhost:4200", "https://untrusted.example")) {
                var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/account/me"))
                        .header("Origin", origin).header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "authorization,content-type")
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
                var response = client.http.send(request, HttpResponse.BodyHandlers.ofString());
                if (origin.equals("http://localhost:4200")) {
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.headers().firstValue("access-control-allow-origin")).contains(origin);
                    assertThat(response.headers().firstValue("access-control-allow-credentials")).isEmpty();
                } else {
                    assertThat(response.statusCode()).isEqualTo(403);
                }
            }
        }
    }

    @Test
    void frameworkErrorsHaveStableCodesAndParameterValidationDetails() throws Exception {
        try (var client=new Client()) {
            client.login(fixture("ADMIN"),PASSWORD);
            assertProblem(client.get("/api/account/unknown"),404,"RESOURCE_NOT_FOUND");
            assertProblem(client.raw("PATCH","/api/account/me",Map.of()),405,"METHOD_NOT_ALLOWED");
            assertProblem(client.get("/api/admin/accounts"),400,"INVALID_REQUEST");
            var invalid=client.get("/api/admin/accounts?email=not-an-email");
            assertProblem(invalid,400,"VALIDATION_FAILED");
            assertThat(body(invalid).path("errors").isArray()).isTrue();
            assertThat(body(invalid).path("errors").isEmpty()).isFalse();
            assertThat(invalid.body()).doesNotContain("not-an-email");
            var unsupported=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/auth/register"))
                    .header("Content-Type","text/plain").POST(HttpRequest.BodyPublishers.ofString("sensitive input")).build();
            assertProblem(client.http.send(unsupported,HttpResponse.BodyHandlers.ofString()),415,"UNSUPPORTED_MEDIA_TYPE");
            client.token=null;
            assertProblem(client.get("/api/account/unknown"),401,"AUTHENTICATION_REQUIRED");
        }
    }

    private JwtClaimsSet.Builder claims(UUID accountId) {
        var now = Instant.now();
        return JwtClaimsSet.builder().issuer("tireshop").audience(List.of("tireshop-api"))
                .subject(accountId.toString()).id(UUID.randomUUID().toString()).issuedAt(now).expiresAt(now.plusSeconds(900))
                .claim("role", "CUSTOMER").claim("authVersion", 0L);
    }
    private String sign(JwtClaimsSet claims, MacAlgorithm algorithm) {
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(algorithm).type("JWT").build(), claims)).getTokenValue();
    }
    private static String newSecret() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String fixture(String role) {
        String email = email();
        jdbc.update("insert into app_account (id,email,password_hash,role,status,first_name,last_name,created_at,updated_at) values (?,?,?,?,'ACTIVE','Test','Account',now(),now())",
                UUID.randomUUID(), email, encoder.encode(PASSWORD), role);
        return email;
    }
    private UUID id(String email) { return jdbc.queryForObject("select id from app_account where email = ?", UUID.class, email); }
    private String email() { return UUID.randomUUID() + "@example.test"; }
    private Map<String, Object> registration(String email) { return Map.of("email", email, "password", PASSWORD, "firstName", "Test", "lastName", "Account"); }
    private Map<String, Object> invitation(String email) { return Map.of("email", email, "firstName", "Test", "lastName", "Employee"); }
    private Map<String, Object> completion(String token) { return Map.of("token", token, "password", NEW_PASSWORD); }
    private Map<String, Object> address() { return Map.of("recipientName", "Test Account", "street", "Testowa", "buildingNumber", "1", "postalCode", "00-001", "city", "Warszawa", "countryCode", "PL"); }
    private JsonNode body(HttpResponse<String> response) { return json.readTree(response.body()); }
    private void assertProblem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).describedAs(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("content-type").orElse("")).contains("application/problem+json");
        assertThat(body(response).get("code").asText()).isEqualTo(code);
    }
    private String sentToken() {
        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(captor.capture());
        var matcher = Pattern.compile("#token=([A-Za-z0-9_-]{43})").matcher(captor.getValue().getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private class Client implements AutoCloseable {
        String token;
        final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> get(String path) throws Exception { return raw("GET", path, null); }
        HttpResponse<String> post(String path, Object value) throws Exception { return raw("POST", path, value); }
        HttpResponse<String> put(String path, Object value) throws Exception { return raw("PUT", path, value); }
        HttpResponse<String> login(String email, String password) throws Exception {
            token = null;
            var response = post("/api/auth/login", Map.of("email", email, "password", password));
            if (response.statusCode() == 200) token = body(response).get("accessToken").asText();
            return response;
        }
        HttpResponse<String> raw(String method, String path, Object value) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(20));
            if (token != null) builder.header("Authorization", "Bearer " + token);
            var publisher = value == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(value));
            return http.send(builder.header("Content-Type", "application/json").method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
        }
        public void close() { http.close(); }
    }
}
