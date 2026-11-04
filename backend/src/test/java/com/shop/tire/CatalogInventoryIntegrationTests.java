package com.shop.tire;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class CatalogInventoryIntegrationTests {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");
    private static final String JWT_SECRET = Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(64));
    private static final String PASSWORD = "Catalog testing password 123!";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> JWT_SECRET);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean JavaMailSender mail;
    private HttpClient http;
    private String employee;
    private String admin;
    private String customer;
    private UUID employeeId;

    @BeforeEach
    void accounts() throws Exception {
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        employee = loginFixture("EMPLOYEE");
        admin = loginFixture("ADMIN");
        customer = loginFixture("CUSTOMER");
        employeeId = UUID.fromString(json.readTree(Base64.getUrlDecoder().decode(employee.split("\\.")[1])).get("sub").asText());
    }

    @AfterEach
    void closeClient() { if (http != null) http.close(); }

    @Test
    void productBecomesPublicOnlyAfterPriceApprovalAndStock() throws Exception {
        var details = details(brand(), "Frigo");
        var created = call(employee, "POST", "/api/employee/tires", details);
        var product = expect(created, 201);
        String id = product.get("id").asText();
        assertThat(created.headers().firstValue("location")).contains("/api/staff/tires/" + id);
        assertThat(product.get("priceGross").isNull()).isTrue();
        assertThat(product.get("onSale").asBoolean()).isFalse();
        assertThat(product.get("availableQuantity").asInt()).isZero();
        assertThat(get(employee, stock(id)).get("reserved").asInt()).isZero();
        problem(call(null, "GET", "/api/tires/" + id, null), 404, "PRODUCT_NOT_FOUND");
        problem(call(admin, "PUT", sale(id), Map.of("onSale", true)), 409, "PRICE_REQUIRED");
        setPrice(id, "299.90");
        expect(call(admin, "PUT", sale(id), Map.of("onSale", true)), 200);
        problem(call(null, "GET", "/api/tires/" + id, null), 404, "PRODUCT_NOT_FOUND");
        receive(id, 8);
        var publicProduct = get(null, "/api/tires/" + id);
        assertThat(publicProduct.get("priceGross").decimalValue()).isEqualByComparingTo("299.90");
        assertThat(publicProduct.get("currency").asText()).isEqualTo("PLN");
        assertThat(publicProduct.get("availableQuantity").asInt()).isEqualTo(8);
        assertThat(publicProduct.has("onHand")).isFalse();

        details.put("model", "Frigo 2");
        details.put("imageUrl", "https://images.example.test/frigo.jpg");
        var edited = expect(call(employee, "PUT", "/api/employee/tires/" + id, details), 200);
        assertThat(edited.get("model").asText()).isEqualTo("Frigo 2");
        assertThat(edited.get("imageUrl").asText()).endsWith("/frigo.jpg");
        assertThat(edited.get("priceGross").decimalValue()).isEqualByComparingTo("299.90");
        assertThat(edited.get("onSale").asBoolean()).isTrue();
        expect(call(admin, "PUT", sale(id), Map.of("onSale", false)), 200);
        problem(call(null, "GET", "/api/tires/" + id, null), 404, "PRODUCT_NOT_FOUND");
        assertThat(get(admin, "/api/staff/tires/" + id).get("availableQuantity").asInt()).isEqualTo(8);
        assertThat(get(employee, stock(id) + "/movements").get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void rolesAreIndependentAndClientsCannotInjectPrivilegedFields() throws Exception {
        String id = create(details(brand(), "Roles"));
        for (String token : List.of(customer, admin)) {
            problem(call(token, "POST", "/api/employee/tires", details(brand(), "Denied")), 403, "ACCESS_DENIED");
            problem(call(token, "PUT", "/api/employee/tires/" + id, details(brand(), "Denied")), 403, "ACCESS_DENIED");
            problem(call(token, "GET", stock(id), null), 403, "ACCESS_DENIED");
            problem(call(token, "POST", stock(id) + "/movements", movement(1)), 403, "ACCESS_DENIED");
        }
        for (String token : List.of(customer, employee)) {
            problem(call(token, "PUT", price(id), Map.of("priceGross", 100)), 403, "ACCESS_DENIED");
            problem(call(token, "PUT", sale(id), Map.of("onSale", true)), 403, "ACCESS_DENIED");
        }
        problem(call(null, "POST", "/api/employee/tires", details(brand(), "Denied")), 401, "AUTHENTICATION_REQUIRED");
        problem(call(null, "PUT", price(id), Map.of("priceGross", 100)), 401, "AUTHENTICATION_REQUIRED");
        problem(call(customer, "GET", "/api/staff/tires", null), 403, "ACCESS_DENIED");
        for (String token : List.of(employee, admin)) expect(call(token, "GET", "/api/staff/tires/" + id, null), 200);

        for (String field : List.of("priceGross", "onSale", "availableQuantity")) {
            var forged = details(brand(), "Forged");
            forged.put(field, 123);
            problem(call(employee, "PUT", "/api/employee/tires/" + id, forged), 400, "INVALID_REQUEST");
        }
        var forgedMovement = movement(5);
        forgedMovement.put("actorAccountId", UUID.randomUUID());
        problem(call(employee, "POST", stock(id) + "/movements", forgedMovement), 400, "INVALID_REQUEST");
        assertThat(get(employee, stock(id)).get("onHand").asInt()).isZero();
    }

    @Test
    void filtersPaginationAndPriceOrderUseOnlyAvailableProducts() throws Exception {
        String brand = brand();
        String low = offer(details(brand, "Low"), "100.00", 1);
        String tie = offer(details(brand, "Tie"), "100.00", 2);
        var summer = details(brand, "Summer");
        summer.put("season", "SUMMER");
        summer.put("widthMm", 225);
        String high = offer(summer, "300.50", 3);
        String noPrice = create(details(brand, "Unpriced"));
        receive(noPrice, 10);
        String withdrawn = offer(details(brand, "Withdrawn"), "10.00", 5);
        expect(call(admin, "PUT", sale(withdrawn), Map.of("onSale", false)), 200);
        String noStock = offer(details(brand, "Empty"), "20.00", 0);
        String reserved = offer(details(brand, "Reserved"), "30.00", 4);
        jdbc.update("update inventory set reserved = on_hand where product_id = ?", UUID.fromString(reserved));
        String manufacturerId = get(employee, "/api/staff/tires/" + low).get("manufacturerId").asText();
        String query = "/api/tires?manufacturerId=" + manufacturerId;
        var first = get(null, query + "&size=2");
        List<String> ties = List.of(low, tie).stream().sorted().toList();
        assertThat(ids(first)).containsExactlyElementsOf(ties);
        assertThat(first.get("totalElements").asLong()).isEqualTo(3);
        assertThat(first.get("totalPages").asInt()).isEqualTo(2);
        assertThat(first.get("page").asInt()).isZero();
        assertThat(ids(get(null, query + "&page=1&size=2"))).containsExactly(high);
        assertThat(ids(get(null, query + "&page=2&size=2"))).isEmpty();
        assertThat(ids(get(null, query + "&sort=PRICE_DESC"))).containsExactly(high, ties.get(0), ties.get(1));
        assertThat(ids(get(null, query + "&widthMm=205&aspectRatio=55&rimDiameterIn=16&season=WINTER")))
                .containsExactlyElementsOf(ties);
        assertThat(ids(get(null, query + "&season=SUMMER&widthMm=225"))).containsExactly(high);
        assertThat(ids(get(null, query + "&aspectRatio=65"))).isEmpty();
        assertThat(ids(get(null, query + "&rimDiameterIn=18"))).isEmpty();
        assertThat(get(admin, "/api/staff/tires?manufacturerId=" + manufacturerId).get("totalElements").asLong()).isEqualTo(7);
        for (String id : List.of(noPrice, withdrawn, noStock, reserved)) {
            problem(call(null, "GET", "/api/tires/" + id, null), 404, "PRODUCT_NOT_FOUND");
        }
        assertThat(ids(get(null, "/api/manufacturers?size=100"))).containsOnlyOnce(manufacturerId);
    }

    @Test
    void normalizesNamesAndRejectsDuplicateVariantsWithoutPartialWrites() throws Exception {
        String name = "Dębica " + UUID.randomUUID();
        String id = create(details("  " + name + "\u00a0 ", " Frigo  2 "));
        String manufacturerId = get(employee, "/api/staff/tires/" + id).get("manufacturerId").asText();
        assertThat(get(employee, "/api/staff/tires/" + id).get("model").asText()).isEqualTo("Frigo 2");
        problem(call(employee, "POST", "/api/employee/tires", details(name.toUpperCase(Locale.ROOT), "FRIGO 2")), 409, "DATA_CONFLICT");
        var variant = details(name.toLowerCase(Locale.ROOT), "frigo 2");
        variant.put("rimDiameterIn", 17);
        String second = create(variant);
        assertThat(get(employee, "/api/staff/tires/" + second).get("manufacturerId").asText()).isEqualTo(manufacturerId);
        problem(call(employee, "PUT", "/api/employee/tires/" + second, details(name, "Frigo 2")), 409, "DATA_CONFLICT");
        assertThat(get(employee, "/api/staff/tires/" + second).get("rimDiameterIn").asInt()).isEqualTo(17);
        assertThat(jdbc.queryForObject("select count(*) from inventory i join tire_product p on p.id=i.product_id where p.manufacturer_id=?",
                Integer.class, UUID.fromString(manufacturerId))).isEqualTo(2);
        assertThat(ids(get(null, "/api/manufacturers?size=100"))).doesNotContain(manufacturerId);
    }

    @Test
    void rejectsInvalidJsonTypesPricesAndProductParameters() throws Exception {
        String id = create(details(brand(), "Validation"));
        for (Object invalid : List.of("205", 205.5, true)) {
            var input = details(brand(), "Invalid width");
            input.put("widthMm", invalid);
            problem(call(employee, "POST", "/api/employee/tires", input), 400, "INVALID_REQUEST");
        }
        for (Object invalid : List.of(0, "0", "INVALID")) {
            var input = details(brand(), "Invalid season");
            input.put("season", invalid);
            problem(call(employee, "POST", "/api/employee/tires", input), 400, "INVALID_REQUEST");
        }
        var numericModel = details(brand(), "Invalid model");
        numericModel.put("model", 123);
        problem(call(employee, "POST", "/api/employee/tires", numericModel), 400, "INVALID_REQUEST");
        for (String field : List.of("widthMm", "aspectRatio", "rimDiameterIn")) {
            var input = details(brand(), "Out of range");
            input.put(field, 1);
            problem(call(employee, "POST", "/api/employee/tires", input), 400, "VALIDATION_FAILED");
        }
        for (String url : List.of("http://images.example.test/tire.jpg", "javascript:alert(1)")) {
            var input = details(brand(), "Invalid image");
            input.put("imageUrl", url);
            problem(call(employee, "POST", "/api/employee/tires", input), 400, "VALIDATION_FAILED");
        }
        for (String amount : List.of("0", "-1", "1.001", "10000000000")) {
            problem(call(admin, "PUT", price(id), Map.of("priceGross", new BigDecimal(amount))), 400, "VALIDATION_FAILED");
        }
        problem(call(admin, "PUT", price(id), Map.of("priceGross", "200.00")), 400, "INVALID_REQUEST");
        problem(call(admin, "PUT", sale(id), Map.of("onSale", "false")), 400, "INVALID_REQUEST");
        problem(call(admin, "PUT", price(id), Map.of()), 400, "VALIDATION_FAILED");
        problem(call(admin, "PUT", sale(id), Map.of()), 400, "VALIDATION_FAILED");
    }

    @Test
    void rejectsMalformedFiltersAndBoundsPages() throws Exception {
        for (String query : List.of("page=-1", "page=1000001", "size=0", "size=101", "size=text",
                "widthMm=1", "aspectRatio=101", "rimDiameterIn=31", "season=0", "sort=model", "manufacturerId=bad")) {
            var response = call(null, "GET", "/api/tires?" + query, null);
            expect(response, 400);
            assertThat(response.headers().firstValue("content-type").orElse("")).contains("application/problem+json");
            assertThat(response.body()).doesNotContain("java.lang", "com.shop", "org.springframework");
        }
        problem(call(null, "GET", "/api/tires/not-a-uuid", null), 400, "INVALID_PARAMETER");
        problem(call(null, "GET", "/api/manufacturers?size=101", null), 400, "VALIDATION_FAILED");
        problem(call(null, "GET", "/api/tires/" + UUID.randomUUID(), null), 404, "PRODUCT_NOT_FOUND");
        String id = create(details(brand(), "Pages"));
        problem(call(employee, "GET", stock(id) + "/movements?page=-1", null), 400, "VALIDATION_FAILED");
        problem(call(employee, "GET", stock(id) + "/movements?size=101", null), 400, "VALIDATION_FAILED");
    }

    @Test
    void stockChangesAreAuditedAndRetriesHaveNoAdditionalEffect() throws Exception {
        String id = create(details(brand(), "Audit"));
        var input = movement(8);
        var first = expect(call(employee, "POST", stock(id) + "/movements", input), 200);
        assertThat(first.get("actorAccountId").asText()).isEqualTo(employeeId.toString());
        assertThat(first.get("onHandAfter").asInt()).isEqualTo(8);
        assertThat(first.get("reservedAfter").asInt()).isZero();
        receive(id, 2);
        assertThat(expect(call(employee, "POST", stock(id) + "/movements", input), 200)).isEqualTo(first);
        assertThat(get(employee, stock(id)).get("onHand").asInt()).isEqualTo(10);
        assertThat(get(employee, stock(id) + "/movements?size=1").get("totalElements").asLong()).isEqualTo(2);
        input.put("quantityDelta", 9);
        problem(call(employee, "POST", stock(id) + "/movements", input), 409, "OPERATION_ID_REUSED");
        var correction = movement(-3);
        correction.put("type", "CORRECTION");
        expect(call(employee, "POST", stock(id) + "/movements", correction), 200);
        assertThat(get(employee, stock(id)).get("available").asInt()).isEqualTo(7);
        assertThat(get(employee, stock(id) + "/movements").get("totalElements").asLong()).isEqualTo(3);
    }

    @Test
    void correctionsCannotConsumeReservedStockAndFailuresLeaveNoAudit() throws Exception {
        String id = create(details(brand(), "Reservations"));
        receive(id, 8);
        jdbc.update("update inventory set reserved=5 where product_id=?", UUID.fromString(id));
        for (int delta : List.of(-4, -9, Integer.MIN_VALUE)) {
            var correction = movement(delta);
            correction.put("type", "CORRECTION");
            problem(call(employee, "POST", stock(id) + "/movements", correction), 409, "INSUFFICIENT_STOCK");
        }
        for (int delta : List.of(0, -1)) {
            problem(call(employee, "POST", stock(id) + "/movements", movement(delta)), 400, "INVALID_STOCK_CHANGE");
        }
        var stock = get(employee, stock(id));
        assertThat(stock.get("onHand").asInt()).isEqualTo(8);
        assertThat(stock.get("reserved").asInt()).isEqualTo(5);
        assertThat(stock.get("available").asInt()).isEqualTo(3);
        assertThat(get(employee, stock(id) + "/movements").get("totalElements").asLong()).isEqualTo(1);
        var correction = movement(-3);
        correction.put("type", "CORRECTION");
        expect(call(employee, "POST", stock(id) + "/movements", correction), 200);
        assertThat(get(employee, stock(id)).get("available").asInt()).isZero();
    }

    @Test
    void integerOverflowCannotCorruptStock() throws Exception {
        String id = create(details(brand(), "Overflow"));
        receive(id, Integer.MAX_VALUE);
        problem(call(employee, "POST", stock(id) + "/movements", movement(1)), 409, "STOCK_LIMIT_EXCEEDED");
        assertThat(get(employee, stock(id)).get("onHand").asInt()).isEqualTo(Integer.MAX_VALUE);
        assertThat(get(employee, stock(id) + "/movements").get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void concurrentReceiptsAndRetriedOperationDoNotLoseOrDuplicateStock() throws Exception {
        String id = create(details(brand(), "Concurrent receipts"));
        var requests = IntStream.range(0, 8).mapToObj(i -> request(employee, "POST", stock(id) + "/movements", movement(1))).toList();
        for (var response : parallel(requests)) expect(response, 200);
        var retry = movement(5);
        requests = IntStream.range(0, 4).mapToObj(i -> request(employee, "POST", stock(id) + "/movements", retry)).toList();
        var responses = parallel(requests);
        for (var response : responses) assertThat(expect(response, 200)).isEqualTo(expect(responses.getFirst(), 200));
        assertThat(get(employee, stock(id)).get("onHand").asInt()).isEqualTo(13);
        assertThat(get(employee, stock(id) + "/movements").get("totalElements").asLong()).isEqualTo(9);
    }

    @Test
    void competingCorrectionsCannotBothTakeLastPieces() throws Exception {
        String id = create(details(brand(), "Last pieces"));
        receive(id, 5);
        var a = movement(-4);
        var b = movement(-4);
        a.put("type", "CORRECTION");
        b.put("type", "CORRECTION");
        var responses = parallel(List.of(request(employee, "POST", stock(id) + "/movements", a),
                request(employee, "POST", stock(id) + "/movements", b)));
        assertThat(responses.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(200, 409);
        assertThat(get(employee, stock(id)).get("onHand").asInt()).isEqualTo(1);
        assertThat(get(employee, stock(id) + "/movements").get("totalElements").asLong()).isEqualTo(2);
    }

    @Test
    void reusedOperationAcrossProductsCannotOverwriteAuditOrLeavePartialStock() throws Exception {
        String a = create(details(brand(), "First"));
        String b = create(details(brand(), "Second"));
        var input = movement(7);
        var responses = parallel(List.of(request(employee, "POST", stock(a) + "/movements", input),
                request(employee, "POST", stock(b) + "/movements", input)));
        assertThat(responses.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(200, 409);
        assertThat(get(employee, stock(a)).get("onHand").asInt() + get(employee, stock(b)).get("onHand").asInt()).isEqualTo(7);
        assertThat(jdbc.queryForObject("select count(*) from inventory_change where id=?", Integer.class, input.get("operationId"))).isEqualTo(1);
        String winner = expect(responses.stream().filter(r -> r.statusCode() == 200).findFirst().orElseThrow(), 200).get("productId").asText();
        assertThat(jdbc.queryForObject("select product_id from inventory_change where id=?", UUID.class, input.get("operationId")).toString()).isEqualTo(winner);
        assertThat(get(employee, stock(winner)).get("onHand").asInt()).isEqualTo(7);
    }

    @Test
    void concurrentProductCreationEnforcesVariantUniqueness() throws Exception {
        var input = details(brand(), "Concurrent variant");
        var responses = parallel(List.of(request(employee, "POST", "/api/employee/tires", input),
                request(employee, "POST", "/api/employee/tires", input)));
        assertThat(responses.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(201, 409);
        String id = expect(responses.stream().filter(r -> r.statusCode() == 201).findFirst().orElseThrow(), 201).get("id").asText();
        assertThat(get(employee, stock(id)).get("onHand").asInt()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from tire_product p join manufacturer m on m.id=p.manufacturer_id where m.name=?", Integer.class, input.get("manufacturer"))).isEqualTo(1);
    }

    @Test
    void concurrentAdminAndEmployeeEditsPreserveBothChanges() throws Exception {
        String brand = brand();
        String id = create(details(brand, "Before"));
        var responses = parallel(List.of(request(employee, "PUT", "/api/employee/tires/" + id, details(brand, "After")),
                request(admin, "PUT", price(id), Map.of("priceGross", new BigDecimal("123.45")))));
        for (var response : responses) expect(response, 200);
        var product = get(admin, "/api/staff/tires/" + id);
        assertThat(product.get("model").asText()).isEqualTo("After");
        assertThat(product.get("priceGross").decimalValue()).isEqualByComparingTo("123.45");
    }

    @Test
    void databaseConstraintsAlsoProtectStockAndSaleRules() throws Exception {
        String id = create(details(brand(), "Constraints"));
        UUID productId = UUID.fromString(id);
        assertThatThrownBy(() -> jdbc.update("update inventory set on_hand=-1 where product_id=?", productId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update inventory set reserved=1 where product_id=?", productId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update tire_product set on_sale=true where id=?", productId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update tire_product set price_gross=0 where id=?", productId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void openApiDistinguishesPublicAndProtectedOperations() throws Exception {
        var document = get(null, "/v3/api-docs");
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/catalog-openapi.json"), document.toPrettyString());
        var paths = document.get("paths");
        for (String path : List.of("/api/tires", "/api/tires/{id}", "/api/manufacturers")) {
            assertThat(paths.get(path).get("get").has("security")).isFalse();
        }
        for (String path : List.of("/api/employee/tires", "/api/staff/tires", "/api/admin/tires/{id}/price", "/api/employee/inventory/{productId}/movements")) {
            for (var operation : paths.get(path)) {
                assertThat(operation.path("security").toString()).contains("bearerAuth");
                assertThat(operation.path("responses").has("401")).isTrue();
                assertThat(operation.path("responses").has("403")).isTrue();
            }
        }
        assertThat(paths.get("/api/employee/tires").get("post").get("responses").has("201")).isTrue();
    }

    private String loginFixture(String role) throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        jdbc.update("insert into app_account (id,email,password_hash,role,status,first_name,last_name,created_at,updated_at) values (?,?,?,?,'ACTIVE','Catalog','Test',now(),now())",
                UUID.randomUUID(), email, encoder.encode(PASSWORD), role);
        return expect(call(null, "POST", "/api/auth/login", Map.of("email", email, "password", PASSWORD)), 200).get("accessToken").asText();
    }
    private String create(Map<String, Object> details) throws Exception {
        return expect(call(employee, "POST", "/api/employee/tires", details), 201).get("id").asText();
    }
    private String offer(Map<String, Object> details, String amount, int quantity) throws Exception {
        String id = create(details);
        setPrice(id, amount);
        expect(call(admin, "PUT", sale(id), Map.of("onSale", true)), 200);
        if (quantity > 0) receive(id, quantity);
        return id;
    }
    private void setPrice(String id, String amount) throws Exception {
        expect(call(admin, "PUT", price(id), Map.of("priceGross", new BigDecimal(amount))), 200);
    }
    private void receive(String id, int quantity) throws Exception { expect(call(employee, "POST", stock(id) + "/movements", movement(quantity)), 200); }
    private String brand() { return "Manufacturer " + UUID.randomUUID(); }
    private String stock(String id) { return "/api/employee/inventory/" + id; }
    private String price(String id) { return "/api/admin/tires/" + id + "/price"; }
    private String sale(String id) { return "/api/admin/tires/" + id + "/sale"; }
    private Map<String, Object> details(String brand, String model) {
        return new HashMap<>(Map.of("manufacturer", brand, "model", model, "widthMm", 205, "aspectRatio", 55, "rimDiameterIn", 16, "season", "WINTER"));
    }
    private Map<String, Object> movement(int delta) {
        return new HashMap<>(Map.of("operationId", UUID.randomUUID(), "type", "RECEIPT", "quantityDelta", delta, "reason", "Test receipt or correction"));
    }
    private List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }
    private JsonNode get(String token, String path) throws Exception { return expect(call(token, "GET", path, null), 200); }
    private JsonNode expect(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).describedAs(response.body()).isEqualTo(status);
        return json.readTree(response.body());
    }
    private void problem(HttpResponse<String> response, int status, String code) {
        var body = expect(response, status);
        assertThat(response.headers().firstValue("content-type").orElse("")).contains("application/problem+json");
        assertThat(body.path("code").asText()).isEqualTo(code);
        assertThat(response.body()).doesNotContain("SQL", "stackTrace", "password_hash");
    }
    private HttpRequest request(String token, String method, String path, Object value) {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(30));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        var publisher = value == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(value));
        return builder.header("Content-Type", "application/json").method(method, publisher).build();
    }
    private HttpResponse<String> call(String token, String method, String path, Object value) throws Exception {
        return http.send(request(token, method, path, value), HttpResponse.BodyHandlers.ofString());
    }
    private List<HttpResponse<String>> parallel(List<HttpRequest> requests) throws Exception {
        var futures = requests.stream().map(request -> http.sendAsync(request, HttpResponse.BodyHandlers.ofString())).toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(40, TimeUnit.SECONDS);
        return futures.stream().map(CompletableFuture::join).toList();
    }
}
