package com.shop.tire;

import com.shop.tire.account.dto.AccountRequests;
import com.shop.tire.account.service.AccountService;
import com.shop.tire.account.service.DemoAccounts;
import com.shop.tire.demo.*;
import java.net.URI;
import java.net.http.*;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.*;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
class DemoSeedIntegrationTests {
    @Container final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
    private final String secret = Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(32));
    private final String password = "Demo-" + UUID.randomUUID();

    @Test
    void demoWorksThroughHttpAndRestartPreservesEditsWithoutDuplicates() throws Exception {
        Map<String, Object> before;
        UUID product;
        try (var app = start("demo", password); var http = HttpClient.newHttpClient()) {
            var jdbc = app.getBean(JdbcTemplate.class);
            assertThat(Arrays.asList(app.getEnvironment().getActiveProfiles())).contains("demo", "local");
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(count(jdbc, "app_account")).isEqualTo(3);
            assertThat(count(jdbc, "tire_product")).isEqualTo(15);
            assertThat(count(jdbc, "customer_order")).isEqualTo(7);
            assertThat(count(jdbc, "demo_seed_history")).isEqualTo(1);
            assertThat(count(jdbc, "order_email_outbox")).isEqualTo(1);
            assertThat(jdbc.queryForList("select distinct status from customer_order", String.class))
                    .containsExactlyInAnyOrder("PENDING_PAYMENT", "PAID", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED");
            assertThat(jdbc.queryForObject("select sum(on_hand) from inventory", Integer.class)).isEqualTo(542);
            assertThat(jdbc.queryForObject("select sum(reserved) from inventory", Integer.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("select count(*) from payment_attempt where status='SUCCEEDED'", Integer.class)).isEqualTo(5);
            assertThat(jdbc.queryForObject("select count(*) from inventory_change where type='SALE'", Integer.class)).isEqualTo(5);
            assertThat(request(app, http, null, "GET", "/api/tires", null, 200).get("totalElements").asInt()).isEqualTo(12);
            String admin = login(app, http, "admin@demo.test");
            String employee = login(app, http, "employee@demo.test");
            String customer = login(app, http, "customer@demo.test");
            assertThat(request(app, http, customer, "GET", "/api/orders", null, 200).get("totalElements").asInt()).isEqualTo(6);
            assertThat(request(app, http, customer, "GET", "/api/cart", null, 200).get("totalQuantity").asInt()).isEqualTo(3);
            request(app, http, admin, "GET", "/api/employee/orders", null, 403);
            request(app, http, employee, "GET", "/api/employee/orders", null, 200);
            product = jdbc.queryForObject("select id from tire_product where model='Demo 1'", UUID.class);
            request(app, http, admin, "PUT", "/api/admin/tires/" + product + "/price", Map.of("priceGross", 777), 200);
            // Emulate a password changed during a presentation: restart must never reset it.
            jdbc.update("update app_account set password_hash=? where email='customer@demo.test'",
                    app.getBean(PasswordEncoder.class).encode("Changed during demo 123!"));
            before = snapshot(jdbc);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var seed = app.getBean(DemoSeedService.class);
                var first = executor.submit(seed::apply);
                var second = executor.submit(seed::apply);
                assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS)).isFalse();
                assertThat(second.get(10, java.util.concurrent.TimeUnit.SECONDS)).isFalse();
            }
        }
        try (var app = start("demo", "")) {
            var jdbc = app.getBean(JdbcTemplate.class);
            assertThat(snapshot(jdbc)).isEqualTo(before);
            assertThat(jdbc.queryForObject("select price_gross from tire_product where id=?", java.math.BigDecimal.class, product))
                    .isEqualByComparingTo("777");
            String hash = jdbc.queryForObject("select password_hash from app_account where email='customer@demo.test'", String.class);
            assertThat(app.getBean(PasswordEncoder.class).matches("Changed during demo 123!", hash)).isTrue();
        }
    }

    @Test
    void normalProfileDoesNotSeedAndCollisionRollsBackEntireDemo() {
        try (var app = start("external", "")) {
            var jdbc = app.getBean(JdbcTemplate.class);
            assertThat(app.getBeansOfType(DemoSeedService.class)).isEmpty();
            assertThat(app.getBeansOfType(DemoAccounts.class)).isEmpty();
            assertThat(count(jdbc, "app_account")).isZero();
            assertThat(count(jdbc, "tire_product")).isZero();
            app.getBean(AccountService.class).register(new AccountRequests.Registration("customer@demo.test", password, "Existing", "Customer"));
        }
        assertThatThrownBy(() -> { try (var ignored = start("demo", password)) {} })
                .hasStackTraceContaining("Demo account email is already in use");
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        assertThat(count(jdbc, "app_account")).isEqualTo(1); // Newly created admin/employee rolled back too.
        assertThat(count(jdbc, "tire_product")).isZero();
        assertThat(count(jdbc, "demo_seed_history")).isZero();
        assertThat(jdbc.queryForObject("select role from app_account", String.class)).isEqualTo("CUSTOMER");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void configuredBootstrapRunsFirstAndDemoDoesNotReplaceItsAdministrator() throws Exception {
        try (var app = start("demo", password, "--app.bootstrap.enabled=true",
                "--app.bootstrap.admin-email=existing-admin@example.test", "--app.bootstrap.admin-password=" + password);
             var http = HttpClient.newHttpClient()) {
            var jdbc = app.getBean(JdbcTemplate.class);
            assertThat(count(jdbc, "app_account")).isEqualTo(3);
            assertThat(jdbc.queryForObject("select email from app_account where role='ADMIN'", String.class))
                    .isEqualTo("existing-admin@example.test");
            login(app, http, "existing-admin@example.test");
            assertThat(count(jdbc, "tire_product")).isEqualTo(15);
        }
    }

    private ConfigurableApplicationContext start(String profile, String demoPassword, String... overrides) {
        var arguments = new ArrayList<>(List.of(
                "--spring.profiles.active=" + profile, "--server.port=0", "--spring.docker.compose.enabled=false",
                "--spring.datasource.url=" + postgres.getJdbcUrl(), "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(), "--app.jwt.secret=" + secret,
                "--app.tracking.secret=" + secret, "--app.demo.password=" + demoPassword,
                "--app.bootstrap.enabled=false", "--app.tracking.mail-job-enabled=false", "--app.order.expiry-job-enabled=false",
                "--management.health.mail.enabled=false", "--spring.main.banner-mode=off"));
        // Replace, rather than repeat, command-line keys (Spring can combine repeated values).
        for (String override : overrides) {
            String key = override.substring(0, override.indexOf('=') + 1);
            arguments.removeIf(argument -> argument.startsWith(key));
            arguments.add(override);
        }
        return new SpringApplicationBuilder(Application.class).registerShutdownHook(false).run(arguments.toArray(String[]::new));
    }

    private String login(ConfigurableApplicationContext app, HttpClient http, String email) throws Exception {
        return request(app, http, null, "POST", "/api/auth/login", Map.of("email", email, "password", password), 200).get("accessToken").asText();
    }
    private JsonNode request(ConfigurableApplicationContext app, HttpClient http, String jwt, String method, String path, Object body, int status) throws Exception {
        var json = app.getBean(ObjectMapper.class);
        int port = app.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json");
        if (jwt != null) builder.header("Authorization", "Bearer " + jwt);
        var response = http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s", method, path).isEqualTo(status);
        return json.readTree(response.body());
    }
    private int count(JdbcTemplate jdbc, String table) {
        // Only fixed test-owned table names, never HTTP input.
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
    private Map<String, Object> snapshot(JdbcTemplate jdbc) {
        return jdbc.queryForMap("""
                select (select count(*) from customer_order) as orders,
                       (select count(*) from inventory_change) as movements,
                       (select sum(on_hand) from inventory) as physical,
                       (select sum(reserved) from inventory) as reserved,
                       (select count(*) from order_email_outbox) as mail,
                       (select count(*) from tire_product) as products,
                       (select count(*) from app_account) as accounts
                """);
    }
}
