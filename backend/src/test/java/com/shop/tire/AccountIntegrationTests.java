package com.shop.tire;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
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
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired org.springframework.session.FindByIndexNameSessionRepository<? extends org.springframework.session.Session> sessions;
    @MockitoBean JavaMailSender mail;

    private static final String PASSWORD = "Testing password 123!";
    private static final String NEW_PASSWORD = "Changed password 456!";

    @Test
    void applicationAndDocumentationRemainPublic() throws Exception {
        try (var client = new Client()) {
            assertThat(client.get("/actuator/health").statusCode()).isEqualTo(200);
            assertThat(client.get("/actuator/health").body()).contains("\"status\":\"UP\"");
            assertThat(client.get("/api/hello").body()).isEqualTo("Hello World!");
            var docs = client.get("/v3/api-docs");
            assertThat(docs.statusCode()).isEqualTo(200);
            assertThat(body(docs).path("paths").has("/api/auth/logout")).isTrue();
            assertThat(client.get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
            assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class)).isEqualTo(2);
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
    void rejectsMissingCsrfRoleInjectionAndInvalidFieldsWithoutLeakingSecrets() throws Exception {
        try (var client = new Client()) {
            var csrf = client.raw("POST", "/api/auth/register", registration(email()), false);
            assertProblem(csrf, 403, "INVALID_CSRF_TOKEN");
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
    void loginPersistsSessionRotatesItAndLogoutRemovesAccess() throws Exception {
        String email = fixture("CUSTOMER");
        try (var client = new Client()) {
            assertProblem(client.post("/api/auth/login", Map.of("email", email, "password", "wrong password")),
                    401, "AUTHENTICATION_FAILED");
            var login = client.login(email, PASSWORD);
            assertThat(login.statusCode()).isEqualTo(200);
            String firstSession = client.cookie("SESSION");
            assertThat(firstSession).isNotBlank();
            assertThat(login.headers().allValues("set-cookie")).anyMatch(cookie ->
                    cookie.startsWith("SESSION=") && cookie.contains("HttpOnly") && cookie.contains("SameSite=Lax"));
            assertThat(client.get("/api/account/me").statusCode()).isEqualTo(200);
            assertThat(jdbc.queryForObject("select count(*) from spring_session where principal_name = ?", Integer.class, email)).isEqualTo(1);
            assertThat(client.login(email, PASSWORD).statusCode()).isEqualTo(200);
            assertThat(client.cookie("SESSION")).isNotEqualTo(firstSession);
            assertThat(client.post("/api/auth/logout", Map.of()).statusCode()).isEqualTo(204);
            assertThat(client.get("/api/account/me").statusCode()).isEqualTo(401);
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
    void passwordResetInvalidatesAllExistingSessionsAndOldPassword() throws Exception {
        String customer = fixture("CUSTOMER");
        try (var admin = new Client(); var old = new Client(); var other = new Client(); var reset = new Client()) {
            admin.login(fixture("ADMIN"), PASSWORD);
            old.login(customer, PASSWORD);
            other.login(customer, PASSWORD);
            assertThat(admin.post("/api/admin/accounts/" + id(customer) + "/password-reset", Map.of()).statusCode()).isEqualTo(204);
            assertThat(reset.post("/api/auth/password-actions/complete", completion(sentToken())).statusCode()).isEqualTo(204);
            assertProblem(old.get("/api/account/me"), 401, "SESSION_EXPIRED");
            assertProblem(other.get("/api/account/me"), 401, "SESSION_EXPIRED");
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
            a.get("/api/auth/csrf");
            b.get("/api/auth/csrf");
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
    void disabledAccountCannotUseExistingSession() throws Exception {
        String customer = fixture("CUSTOMER");
        try (var user = new Client()) {
            user.login(customer, PASSWORD);
            jdbc.update("update app_account set status = 'DISABLED' where email = ?", customer);
            assertProblem(user.get("/api/account/me"), 401, "SESSION_EXPIRED");
            assertThat(user.login(customer, PASSWORD).statusCode()).isEqualTo(401);
        }
    }

    private int uncheckedComplete(Client client, String token) {
        try { return client.raw("POST", "/api/auth/password-actions/complete", completion(token), true).statusCode(); }
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
    void absoluteSessionTimeoutRequiresNewLogin() throws Exception {
        String email = fixture("CUSTOMER");
        try (var client = new Client()) {
            client.login(email, PASSWORD);
            expireSession(sessions, email);
            assertProblem(client.get("/api/account/me"), 401, "SESSION_EXPIRED");
        }
    }

    private <S extends org.springframework.session.Session> void expireSession(
            org.springframework.session.FindByIndexNameSessionRepository<S> repository, String email) {
        S session = repository.findByPrincipalName(email).values().iterator().next();
        session.setAttribute("tireshop.loginTime", java.time.Instant.now().minus(Duration.ofHours(9)));
        repository.save(session);
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
        var matcher = Pattern.compile("token=([A-Za-z0-9_-]{43})").matcher(captor.getValue().getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private class Client implements AutoCloseable {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> get(String path) throws Exception { return raw("GET", path, null, false); }
        HttpResponse<String> post(String path, Object value) throws Exception { get("/api/auth/csrf"); return raw("POST", path, value, true); }
        HttpResponse<String> put(String path, Object value) throws Exception { get("/api/auth/csrf"); return raw("PUT", path, value, true); }
        HttpResponse<String> login(String email, String password) throws Exception { return post("/api/auth/login", Map.of("email", email, "password", password)); }
        String cookie(String name) { return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals(name)).map(HttpCookie::getValue).findFirst().orElse(""); }
        HttpResponse<String> raw(String method, String path, Object value, boolean csrf) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(20));
            if (csrf) builder.header("X-XSRF-TOKEN", cookie("XSRF-TOKEN"));
            var publisher = value == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(value));
            return http.send(builder.header("Content-Type", "application/json").method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
        }
        public void close() { http.close(); }
    }
}
