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
            assertThat(count(jdbc, "app_account")).isEqualTo(4);
            assertThat(count(jdbc, "tire_product")).isEqualTo(24);
            assertThat(count(jdbc, "customer_order")).isEqualTo(30);
            assertThat(count(jdbc, "demo_seed_history")).isEqualTo(2);
            assertThat(count(jdbc, "order_email_outbox")).isEqualTo(2);
            assertThat(jdbc.queryForList("select distinct status from customer_order", String.class))
                    .containsExactlyInAnyOrder("PENDING_PAYMENT", "PAID", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED");
            assertThat(jdbc.queryForObject("select sum(on_hand) from inventory", Integer.class)).isEqualTo(891);
            assertThat(jdbc.queryForObject("select sum(reserved) from inventory", Integer.class)).isEqualTo(4);
            assertThat(jdbc.queryForObject("select count(*) from payment_attempt where status='SUCCEEDED'", Integer.class)).isEqualTo(8);
            assertThat(jdbc.queryForObject("select count(*) from inventory_change where type='SALE'", Integer.class)).isEqualTo(9);
            assertThat(request(app, http, null, "GET", "/api/tires", null, 200).get("totalElements").asInt()).isEqualTo(21);
            String admin = login(app, http, "admin@demo.test");
            String employee = login(app, http, "employee@demo.test");
            String customer = login(app, http, "customer@demo.test");
            String history = login(app, http, "history@demo.test");
            request(app, http, history, "GET", "/api/account/me/address", null, 404);
            var historyPage = request(app, http, history, "GET", "/api/orders?page=1&size=20", null, 200);
            assertThat(historyPage.get("totalElements").asInt()).isEqualTo(21);
            assertThat(historyPage.get("items").size()).isEqualTo(1);
            UUID paymentOrder = jdbc.queryForObject("select order_id from payment_attempt group by order_id having count(*)=21", UUID.class);
            var mixedOrder = request(app, http, history, "GET", "/api/orders/" + paymentOrder, null, 200);
            assertThat(mixedOrder.at("/snapshot/items").size()).isEqualTo(2);
            assertThat(mixedOrder.at("/snapshot/shippingGross").decimalValue()).isEqualByComparingTo("0");
            UUID shippedOrder = jdbc.queryForObject("select id from customer_order where tracking_number like 'DEMO-NEW-%'", UUID.class);
            assertThat(request(app, http, history, "GET", "/api/orders/" + shippedOrder, null, 200)
                    .at("/snapshot/shippingGross").decimalValue()).isEqualByComparingTo("20");
            assertThat(jdbc.queryForObject("select count(*) from payment_attempt where status='PENDING'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from payment_attempt where reason='ORDER_CANCELLED'", Integer.class)).isEqualTo(2);
            var payments = request(app, http, history, "GET", "/api/orders/" + paymentOrder + "/payments?size=100", null, 200);
            assertThat(payments.get("totalElements").asInt()).isEqualTo(21);
            assertThat(payments.get("items").valueStream().map(item -> item.get("status").asText()).toList())
                    .contains("FAILED", "CANCELLED", "SUCCEEDED");
            assertThat(request(app, http, history, "GET", "/api/orders/" + paymentOrder + "/payments?page=1&size=20", null, 200)
                    .get("items").size()).isEqualTo(1);
            request(app, http, customer, "GET", "/api/orders/" + paymentOrder + "/payments", null, 404);
            UUID stockProduct = jdbc.queryForObject("select id from tire_product where model='Demo 16'", UUID.class);
            var movements = request(app, http, employee, "GET", "/api/employee/inventory/" + stockProduct + "/movements?size=100", null, 200);
            assertThat(movements.get("totalElements").asInt()).isEqualTo(23);
            assertThat(movements.get("items").valueStream().map(item -> item.get("type").asText()).toList())
                    .contains("RECEIPT", "CORRECTION", "SALE");
            assertThat(jdbc.queryForObject("select sum(quantity_delta) from inventory_change where type='CORRECTION'", Integer.class)).isZero();
            var audit = request(app, http, admin, "GET", "/api/admin/audit-log?page=2&size=20", null, 200);
            assertThat(audit.get("totalElements").asInt()).isEqualTo(50);
            assertThat(audit.get("items").size()).isEqualTo(10);
            assertThat(request(app, http, admin, "GET", "/api/admin/audit-log?eventType=ORDER_CANCELLED", null, 200)
                    .get("totalElements").asInt()).isEqualTo(20);
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
            assertThat(count(jdbc, "app_account")).isEqualTo(4);
            assertThat(jdbc.queryForObject("select email from app_account where role='ADMIN'", String.class))
                    .isEqualTo("existing-admin@example.test");
            login(app, http, "existing-admin@example.test");
            assertThat(count(jdbc, "tire_product")).isEqualTo(24);
        }
    }

    @Test
    void upgradingAnEditedV1DatabasePreservesItsAccountsProductAndCart() throws Exception {
        UUID product;
        JsonNode cart;
        List<Map<String, Object>> originalAccounts;
        Map<String, Object> originalProduct;
        try (var app = start("external", ""); var http = HttpClient.newHttpClient()) {
            var jdbc = app.getBean(JdbcTemplate.class);
            // A minimal v1 database after a presentation, including an existing custom price and cart.
            prepareV1Accounts(app);
            String admin = login(app, http, "admin@demo.test");
            String employee = login(app, http, "employee@demo.test");
            String customer = login(app, http, "customer@demo.test");
            var tire = request(app, http, employee, "POST", "/api/employee/tires", Map.of(
                    "manufacturer", "Existing", "model", "Edited demo", "widthMm", 205, "aspectRatio", 55,
                    "rimDiameterIn", 16, "season", "SUMMER"), 201);
            product = UUID.fromString(tire.get("id").asText());
            request(app, http, admin, "PUT", "/api/admin/tires/" + product + "/price", Map.of("priceGross", 777), 200);
            request(app, http, admin, "PUT", "/api/admin/tires/" + product + "/sale", Map.of("onSale", true), 200);
            request(app, http, employee, "POST", "/api/employee/inventory/" + product + "/movements", Map.of(
                    "operationId", UUID.randomUUID(), "type", "RECEIPT", "quantityDelta", 10, "reason", "Existing stock"), 200);
            cart = request(app, http, customer, "PUT", "/api/cart/items/" + product, Map.of("quantity", 2), 200);
            originalAccounts = jdbc.queryForList("select * from app_account order by email");
            originalProduct = jdbc.queryForMap("select * from tire_product where id=?", product);
        }
        try (var app = start("demo", password); var http = HttpClient.newHttpClient()) {
            var jdbc = app.getBean(JdbcTemplate.class);
            assertThat(count(jdbc, "demo_seed_history")).isEqualTo(2);
            assertThat(count(jdbc, "tire_product")).isEqualTo(10); // Only the nine v2 products were appended.
            assertThat(jdbc.queryForList("select * from app_account where email<>'history@demo.test' order by email"))
                    .isEqualTo(originalAccounts);
            assertThat(jdbc.queryForMap("select * from tire_product where id=?", product)).isEqualTo(originalProduct);
            assertThat(request(app, http, login(app, http, "customer@demo.test"), "GET", "/api/cart", null, 200)).isEqualTo(cart);
            assertThat(request(app, http, login(app, http, "history@demo.test"), "GET", "/api/orders", null, 200)
                    .get("totalElements").asInt()).isEqualTo(21);
        }
    }

    @Test
    void upgradeRefusesToTakeOverAnExistingHistoryAccount() {
        Map<String, Object> before;
        try (var app = start("external", "")) {
            prepareV1Accounts(app);
            app.getBean(AccountService.class).register(new AccountRequests.Registration("history@demo.test", password, "Existing", "Customer"));
            before = snapshot(app.getBean(JdbcTemplate.class));
        }
        assertThatThrownBy(() -> { try (var ignored = start("demo", password)) {} })
                .hasStackTraceContaining("Demo account email is already in use");
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        assertThat(snapshot(jdbc)).isEqualTo(before);
        assertThat(jdbc.queryForList("select name from demo_seed_history", String.class)).containsExactly("presentation-v1");
        assertThat(jdbc.queryForObject("select first_name from app_account where email='history@demo.test'", String.class)).isEqualTo("Existing");
    }

    private void prepareV1Accounts(ConfigurableApplicationContext app) {
        for (String name : List.of("admin", "employee", "customer")) {
            app.getBean(AccountService.class).register(new AccountRequests.Registration(name + "@demo.test", password, name, "Existing"));
        }
        var jdbc = app.getBean(JdbcTemplate.class);
        jdbc.update("update app_account set role='ADMIN' where email='admin@demo.test'");
        jdbc.update("update app_account set role='EMPLOYEE' where email='employee@demo.test'");
        jdbc.update("insert into demo_seed_history(name, applied_at) values ('presentation-v1', now())");
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
                       (select count(*) from payment_attempt) as payments,
                       (select count(*) from audit_log) as audit,
                       (select count(*) from demo_seed_history) as seeds,
                       (select count(*) from tire_product) as products,
                       (select count(*) from app_account) as accounts
                """);
    }
}
