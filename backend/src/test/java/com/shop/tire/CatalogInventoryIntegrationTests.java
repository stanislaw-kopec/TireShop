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
    private static final String TRACKING_SECRET = Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(32));
    private static final String PASSWORD = "Catalog testing password 123!";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("app.order.expiry-job-enabled", () -> false);
        registry.add("app.jwt.secret", () -> JWT_SECRET);
        registry.add("app.tracking.secret", () -> TRACKING_SECRET);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired com.shop.tire.order.service.OrderService orders;
    @Autowired com.shop.tire.order.service.OrderMailDelivery delivery;
    @Autowired java.time.Clock clock;
    @Autowired org.springframework.context.ApplicationEventPublisher events;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
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
        jdbc.update("UPDATE order_email_outbox SET next_attempt_at=now()+interval '1 day' WHERE status='PENDING'");
        jdbc.update("DELETE FROM tracking_request_limit");
        jdbc.update("DELETE FROM audit_log");
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

    @Test
    void cartsCalculateDeliveryFromAllProductsAndDoNotReserveStock() throws Exception {
        String a = offer(details(brand(), "Cart A"), "100.10", 10);
        String b = offer(details(brand(), "Cart B"), "50.20", 10);
        String token = newGuest();
        expect(guestCall(token, "PUT", "/items/" + a, Map.of("quantity", 3)), 200);
        var three = expect(guestCall(token, "GET", "", null), 200);
        assertThat(three.get("shippingGross").decimalValue()).isEqualByComparingTo("20.00");
        var four = expect(guestCall(token, "PUT", "/items/" + b, Map.of("quantity", 1)), 200);
        assertThat(four.get("shippingGross").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(four.get("totalGross").decimalValue()).isEqualByComparingTo("350.50");
        assertThat(four.get("readyForCheckout").asBoolean()).isTrue();
        assertThat(get(employee, stock(a)).get("reserved").asInt()).isZero();
        setPrice(a, "200.00");
        assertThat(expect(guestCall(token, "GET", "", null), 200).get("totalGross").decimalValue()).isEqualByComparingTo("650.20");
        expect(guestCall(token, "DELETE", "/items/" + a, null), 200);
        var empty = expect(guestCall(token, "DELETE", "/items/" + b, null), 200);
        assertThat(empty.get("readyForCheckout").asBoolean()).isFalse();
        assertThat(empty.get("totalGross").decimalValue()).isZero();
    }

    @Test
    void customerCartsArePrivateAndGuestTokensAreNotJwtOrCookies() throws Exception {
        String id = offer(details(brand(), "Private cart"), "90.00", 10);
        String other = loginFixture("CUSTOMER");
        String guest = newGuest();
        expect(call(customer, "PUT", "/api/cart/items/" + id, Map.of("quantity", 2)), 200);
        assertThat(get(other, "/api/cart").get("items").isEmpty()).isTrue();
        assertThat(expect(guestCall(guest, "GET", "", null), 200).get("items").isEmpty()).isTrue();
        problem(call(null, "GET", "/api/cart", null), 401, "AUTHENTICATION_REQUIRED");
        problem(call(employee, "GET", "/api/cart", null), 403, "ACCESS_DENIED");
        problem(call(admin, "GET", "/api/cart", null), 403, "ACCESS_DENIED");
        problem(guestCall(null, "GET", "", null), 404, "CART_NOT_FOUND");
        problem(guestCall(customer, "GET", "", null), 404, "CART_NOT_FOUND");
        problem(call(null, "GET", "/api/guest/cart?token=" + guest, null), 404, "CART_NOT_FOUND");
        var cookie = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/guest/cart")).header("Cookie", "cart=" + guest).GET().build();
        problem(http.send(cookie, HttpResponse.BodyHandlers.ofString()), 404, "CART_NOT_FOUND");
        assertThat(jdbc.queryForObject("select count(*) from shopping_cart where guest_token_hash=?", Integer.class, guest)).isZero();
    }

    @Test
    void cartWritesValidateQuantityAndShowWithdrawnItemsWithoutLosingThem() throws Exception {
        String id = offer(details(brand(), "Availability"), "50.00", 4);
        String token = newGuest();
        problem(guestCall(token, "PUT", "/items/" + id, Map.of("quantity", 5)), 409, "CART_PRODUCT_UNAVAILABLE");
        problem(guestCall(token, "PUT", "/items/" + id, Map.of("quantity", 0)), 400, "VALIDATION_FAILED");
        problem(guestCall(token, "PUT", "/items/" + id, Map.of("quantity", "2")), 400, "INVALID_REQUEST");
        problem(guestCall(token, "PUT", "/items/" + id, Map.of("quantity", 1, "priceGross", 1)), 400, "INVALID_REQUEST");
        expect(guestCall(token, "PUT", "/items/" + id, Map.of("quantity", 4)), 200);
        expect(guestCall(token, "PUT", "/items/" + id, Map.of("quantity", 4)), 200);
        assertThat(expect(guestCall(token, "GET", "", null), 200).get("totalQuantity").asLong()).isEqualTo(4);
        jdbc.update("update inventory set reserved=1 where product_id=?", UUID.fromString(id));
        var reduced = expect(guestCall(token, "GET", "", null), 200);
        assertThat(reduced.get("readyForCheckout").asBoolean()).isFalse();
        assertThat(reduced.at("/items/0/issue").asText()).isEqualTo("INSUFFICIENT_STOCK");
        expect(call(admin, "PUT", sale(id), Map.of("onSale", false)), 200);
        assertThat(expect(guestCall(token, "GET", "", null), 200).at("/items/0/issue").asText()).isEqualTo("NOT_FOR_SALE");
        expect(guestCall(token, "DELETE", "/items/" + id, null), 200);
    }

    @Test
    void mergeIsAtomicAndCanBeRetriedWithoutDoublingItems() throws Exception {
        String id = offer(details(brand(), "Merge"), "10.00", 3);
        String token = newGuest();
        expect(guestCall(token, "PUT", "/items/" + id, Map.of("quantity", 2)), 200);
        expect(call(customer, "PUT", "/api/cart/items/" + id, Map.of("quantity", 2)), 200);
        problem(mergeCall(customer, token), 409, "CART_MERGE_UNAVAILABLE");
        assertThat(get(customer, "/api/cart").get("totalQuantity").asLong()).isEqualTo(2);
        assertThat(expect(guestCall(token, "GET", "", null), 200).get("totalQuantity").asLong()).isEqualTo(2);
        receive(id, 2);
        assertThat(expect(mergeCall(customer, token), 200).get("totalQuantity").asLong()).isEqualTo(4);
        assertThat(expect(mergeCall(customer, token), 200).get("totalQuantity").asLong()).isEqualTo(4);
        problem(guestCall(token, "GET", "", null), 404, "CART_NOT_FOUND");
        problem(mergeCall(loginFixture("CUSTOMER"), token), 404, "CART_NOT_FOUND");
        assertThat(get(employee, stock(id)).get("reserved").asInt()).isZero();
    }

    @Test
    void concurrentMergesDoNotLoseDifferentProductsOrDoubleSameGuest() throws Exception {
        String a = offer(details(brand(), "Merge A"), "10.00", 10);
        String b = offer(details(brand(), "Merge B"), "10.00", 10);
        String first = newGuest();
        String second = newGuest();
        expect(guestCall(first, "PUT", "/items/" + a, Map.of("quantity", 2)), 200);
        expect(guestCall(second, "PUT", "/items/" + b, Map.of("quantity", 2)), 200);
        var requests = List.of(mergeRequest(customer, first), mergeRequest(customer, first), mergeRequest(customer, second));
        for (var response : parallel(requests)) expect(response, 200);
        var result = get(customer, "/api/cart");
        assertThat(result.get("totalQuantity").asLong()).isEqualTo(4);
        assertThat(result.get("items").size()).isEqualTo(2);
    }

    @Test
    void guestExpiryIsEnforcedForReadingWritingAndMerging() throws Exception {
        String token = newGuest();
        String hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        jdbc.update("update shopping_cart set expires_at=now()-interval '1 second' where guest_token_hash=?", hash);
        problem(guestCall(token, "GET", "", null), 404, "CART_NOT_FOUND");
        problem(guestCall(token, "DELETE", "/items/" + UUID.randomUUID(), null), 404, "CART_NOT_FOUND");
        problem(mergeCall(customer, token), 404, "CART_NOT_FOUND");
    }

    private String newGuest() throws Exception {
        var response = call(null, "POST", "/api/guest/cart", null);
        assertThat(response.headers().allValues("set-cookie")).isEmpty();
        assertThat(response.headers().firstValue("cache-control").orElse("")).contains("no-store");
        return expect(response, 201).get("token").asText();
    }
    private HttpResponse<String> guestCall(String token, String method, String suffix, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(request(null, method, "/api/guest/cart" + suffix, body), (name, value) -> true);
        if (token != null) builder.header("X-Cart-Token", token);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpRequest mergeRequest(String jwt, String guest) {
        return HttpRequest.newBuilder(request(jwt, "POST", "/api/cart/merge", null), (name, value) -> true).header("X-Cart-Token", guest).build();
    }
    private HttpResponse<String> mergeCall(String jwt, String guest) throws Exception {
        return http.send(mergeRequest(jwt, guest), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void checkoutCreatesImmutableOrderAndReservationOnlyAfterConfirmation() throws Exception {
        String a = offer(details(brand(), "Order A"), "100.10", 10);
        String b = offer(details(brand(), "Order B"), "50.20", 10);
        expect(call(customer,"PUT","/api/cart/items/"+a,Map.of("quantity",3)),200);
        expect(call(customer,"PUT","/api/cart/items/"+b,Map.of("quantity",1)),200);
        expect(call(customer,"PUT","/api/account/me/address",deliveryAddress()),200);
        var input = new HashMap<String,Object>();
        input.put("phone","123456789");
        var quote = expect(call(customer,"POST","/api/orders/preview",input),200);
        assertThat(get(employee,stock(a)).get("reserved").asInt()).isZero();
        var checkout = Map.of("quoteId",quote.get("id").asText(),"checkoutKey",UUID.randomUUID());
        var created = call(customer,"POST","/api/orders",checkout);
        var order = expect(created,201);
        String id = order.get("id").asText();
        assertThat(created.headers().firstValue("location")).contains("/api/orders/"+id);
        assertThat(order.get("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.at("/snapshot/totalGross").decimalValue()).isEqualByComparingTo("350.50");
        assertThat(order.at("/snapshot/shippingGross").decimalValue()).isZero();
        assertThat(java.time.Duration.between(java.time.Instant.parse(order.get("createdAt").asText()),java.time.Instant.parse(order.get("reservationExpiresAt").asText())))
                .isEqualTo(java.time.Duration.ofMinutes(15));
        assertThat(get(employee,stock(a)).get("onHand").asInt()).isEqualTo(10);
        assertThat(get(employee,stock(a)).get("reserved").asInt()).isEqualTo(3);
        assertThat(get(customer,"/api/cart").get("items").isEmpty()).isTrue();
        assertThat(expect(call(customer,"POST","/api/orders",checkout),201)).isEqualTo(order);
        setPrice(a,"999.00");
        var address = new HashMap<>(deliveryAddress()); address.put("city","Gdańsk");
        expect(call(customer,"PUT","/api/account/me/address",address),200);
        assertThat(get(customer,"/api/orders/"+id)).isEqualTo(order);
        assertThat(get(customer,"/api/orders?size=1").get("totalElements").asLong()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from inventory_change where product_id=?",Integer.class,UUID.fromString(a))).isEqualTo(1);
    }

    @Test
    void changedPricesOrCartRequireFreshConfirmationWithoutPartialReservation() throws Exception {
        String id = offer(details(brand(),"Price confirmation"),"10.00",5);
        expect(call(customer,"PUT","/api/cart/items/"+id,Map.of("quantity",3)),200);
        var quote = preview(customer);
        assertThat(quote.at("/snapshot/shippingGross").decimalValue()).isEqualByComparingTo("20.00");
        setPrice(id,"11.00");
        var key = UUID.randomUUID();
        problem(call(customer,"POST","/api/orders",Map.of("quoteId",quote.get("id").asText(),"checkoutKey",key)),409,"CHECKOUT_CHANGED");
        assertThat(get(employee,stock(id)).get("reserved").asInt()).isZero();
        quote = preview(customer);
        expect(call(customer,"PUT","/api/cart/items/"+id,Map.of("quantity",2)),200);
        problem(call(customer,"POST","/api/orders",Map.of("quoteId",quote.get("id").asText(),"checkoutKey",key)),409,"CHECKOUT_CHANGED");
        var fresh = preview(customer);
        expect(call(customer,"POST","/api/orders",Map.of("quoteId",fresh.get("id").asText(),"checkoutKey",key)),201);
        assertThat(get(employee,stock(id)).get("reserved").asInt()).isEqualTo(2);
    }

    @Test
    void concurrentBuyersCannotReserveMoreThanAvailableAndLoserKeepsCart() throws Exception {
        String a = offer(details(brand(),"Last order A"),"10.00",2);
        String b = offer(details(brand(),"Last order B"),"10.00",1);
        String other = loginFixture("CUSTOMER");
        for (String token : List.of(customer,other)) {
            expect(call(token,"PUT","/api/cart/items/"+a,Map.of("quantity",1)),200);
            expect(call(token,"PUT","/api/cart/items/"+b,Map.of("quantity",1)),200);
        }
        var first = preview(customer); var second = preview(other);
        var responses = parallel(List.of(request(customer,"POST","/api/orders",Map.of("quoteId",first.get("id").asText(),"checkoutKey",UUID.randomUUID())),
                request(other,"POST","/api/orders",Map.of("quoteId",second.get("id").asText(),"checkoutKey",UUID.randomUUID()))));
        assertThat(responses.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(201,409);
        assertThat(get(employee,stock(a)).get("reserved").asInt()).isEqualTo(1);
        assertThat(get(employee,stock(b)).get("reserved").asInt()).isEqualTo(1);
        String loser = responses.getFirst().statusCode()==409 ? customer : other;
        assertThat(get(loser,"/api/cart").get("totalQuantity").asLong()).isEqualTo(2);
    }

    @Test
    void parallelCheckoutRetryCreatesOnlyOneOrderAndRejectsDifferentKeyOrQuote() throws Exception {
        String id = offer(details(brand(),"Retry checkout"),"10.00",4);
        expect(call(customer,"PUT","/api/cart/items/"+id,Map.of("quantity",2)),200);
        var quote = preview(customer); var second = preview(customer);
        UUID key = UUID.randomUUID();
        var input = Map.of("quoteId",quote.get("id").asText(),"checkoutKey",key);
        var responses = parallel(List.of(request(customer,"POST","/api/orders",input),request(customer,"POST","/api/orders",input)));
        assertThat(expect(responses.get(0),201)).isEqualTo(expect(responses.get(1),201));
        problem(call(customer,"POST","/api/orders",Map.of("quoteId",quote.get("id").asText(),"checkoutKey",UUID.randomUUID())),409,"QUOTE_ALREADY_USED");
        problem(call(customer,"POST","/api/orders",Map.of("quoteId",second.get("id").asText(),"checkoutKey",key)),409,"CHECKOUT_KEY_REUSED");
        assertThat(get(employee,stock(id)).get("reserved").asInt()).isEqualTo(2);
    }

    @Test
    void guestPurchaseContextSurvivesLoginAndCartMergeWithoutChangingOrderOwner() throws Exception {
        String id = offer(details(brand(),"Guest order"),"10.00",8);
        String guest = newGuest();
        expect(guestCall(guest,"PUT","/items/"+id,Map.of("quantity",2)),200);
        var quoteResponse = headerCall(null,"X-Cart-Token",guest,"POST","/api/guest/orders/preview",previewInput());
        var quote = expect(quoteResponse,200);
        assertThat(quoteResponse.headers().firstValue("cache-control").orElse("")).contains("no-store");
        String purchase = quote.get("purchaseToken").asText();
        assertThat(purchase).hasSize(43).isNotEqualTo(guest);
        var input = Map.of("quoteId",quote.get("id").asText(),"checkoutKey",UUID.randomUUID());
        var order = expect(headerCall(null,"X-Purchase-Token",purchase,"POST","/api/guest/orders",input),201);
        String orderId = order.get("id").asText();
        problem(headerCall(null,"X-Purchase-Token",guest,"GET","/api/guest/orders/"+orderId,null),404,"ORDER_NOT_FOUND");
        problem(call(customer,"GET","/api/orders/"+orderId,null),404,"ORDER_NOT_FOUND");
        expect(mergeCall(customer,guest),200);
        assertThat(expect(headerCall(customer,"X-Purchase-Token",purchase,"GET","/api/guest/orders/"+orderId,null),200)).isEqualTo(order);
        assertThat(expect(headerCall(null,"X-Purchase-Token",purchase,"POST","/api/guest/orders",input),201)).isEqualTo(order);
        assertThat(jdbc.queryForObject("select guest_token_hash from customer_order where id=?",String.class,UUID.fromString(orderId))).hasSize(64).isNotEqualTo(purchase);
        assertThat(order.toString()).doesNotContain(purchase,"guestHash");
        expect(headerCall(null,"X-Purchase-Token",purchase,"POST","/api/guest/orders/"+orderId+"/cancellation",null),200);
        assertThat(get(employee,stock(id)).get("reserved").asInt()).isZero();
    }

    @Test
    void cancellationsAndExpiryReleaseExactlyOnceAndNeverChangePhysicalStock() throws Exception {
        String id = offer(details(brand(),"Expiry"),"10.00",8);
        var order = createCustomerOrder(id,3);
        String orderId = order.get("id").asText();
        var requests = List.of(request(customer,"POST","/api/orders/"+orderId+"/cancellation",null),request(customer,"POST","/api/orders/"+orderId+"/cancellation",null));
        for (var response : parallel(requests)) assertThat(expect(response,200).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(get(employee,stock(id)).get("reserved").asInt()).isZero();
        assertThat(get(employee,stock(id)).get("onHand").asInt()).isEqualTo(8);
        var expired = createCustomerOrder(id,4);
        UUID expiredId = UUID.fromString(expired.get("id").asText());
        jdbc.update("update customer_order set created_at=now()-interval '16 minutes',reservation_expires_at=now()-interval '1 minute' where id=?",expiredId);
        assertThat(get(customer,"/api/orders/"+expiredId).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select status from customer_order where id=?",String.class,expiredId)).isEqualTo("PENDING_PAYMENT");
        var expiry = CompletableFuture.runAsync(()->orders.expire(expiredId));
        expect(call(customer,"POST","/api/orders/"+expiredId+"/cancellation",null),200);
        expiry.get(20,TimeUnit.SECONDS);
        orders.expire(expiredId);
        assertThat(get(employee,stock(id)).get("reserved").asInt()).isZero();
        assertThat(get(customer,"/api/orders/"+expiredId).get("cancellationReason").asText()).isEqualTo("EXPIRED");
    }

    @Test
    void privateOrdersRejectOtherOwnersStaffInvalidInputAndPaidCancellation() throws Exception {
        String id = offer(details(brand(),"Order privacy"),"10.00",8);
        var order = createCustomerOrder(id,1);
        String path = "/api/orders/"+order.get("id").asText();
        String other = loginFixture("CUSTOMER");
        problem(call(other,"GET",path,null),404,"ORDER_NOT_FOUND");
        problem(call(other,"POST",path+"/cancellation",null),404,"ORDER_NOT_FOUND");
        problem(call(null,"GET",path,null),401,"AUTHENTICATION_REQUIRED");
        problem(call(employee,"GET",path,null),403,"ACCESS_DENIED");
        problem(call(admin,"POST",path+"/cancellation",null),403,"ACCESS_DENIED");
        problem(call(customer,"GET","/api/orders?size=101",null),400,"VALIDATION_FAILED");
        problem(call(customer,"POST","/api/orders/preview",previewInput()),409,"EMPTY_CART");
        var forged = new HashMap<>(previewInput()); forged.put("totalGross",1);
        problem(call(customer,"POST","/api/orders/preview",forged),400,"INVALID_REQUEST");
        problem(call(customer,"POST","/api/orders",Map.of("quoteId","bad","checkoutKey",UUID.randomUUID())),400,"INVALID_REQUEST");
        jdbc.update("update customer_order set status='PAID' where id=?",UUID.fromString(order.get("id").asText()));
        problem(call(customer,"POST",path+"/cancellation",null),409,"ORDER_NOT_CANCELLABLE");
    }

    @Test
    void expiredQuoteDoesNotCreateAnOrderAndGuestAddressIsRequired() throws Exception {
        String id = offer(details(brand(),"Old quote"),"10.00",5);
        expect(call(customer,"PUT","/api/cart/items/"+id,Map.of("quantity",2)),200);
        var quote = preview(customer);
        UUID quoteId = UUID.fromString(quote.get("id").asText());
        jdbc.update("update checkout_quote set expires_at=now()-interval '1 second' where id=?",quoteId);
        problem(call(customer,"POST","/api/orders",Map.of("quoteId",quoteId,"checkoutKey",UUID.randomUUID())),409,"QUOTE_EXPIRED");
        assertThat(get(employee,stock(id)).get("reserved").asInt()).isZero();
        String guest = newGuest();
        problem(headerCall(null,"X-Cart-Token",guest,"POST","/api/guest/orders/preview",Map.of("phone","123456789")),400,"DELIVERY_REQUIRED");
    }

    @Test
    void orderInsertFailureRollsBackAlreadyFlushedReservationAndPreservesCart() throws Exception {
        String product = offer(details(brand(),"Rollback after reservation"),"20.00",5);
        expect(call(customer,"PUT","/api/cart/items/"+product,Map.of("quantity",2)),200);
        var quote = preview(customer);
        UUID key = UUID.randomUUID();
        // Fault injection in this isolated PostgreSQL database, limited to this generated key.
        jdbc.execute("ALTER TABLE customer_order ADD CONSTRAINT test_checkout_failure CHECK (checkout_key <> '"+key+"'::uuid)");
        try {
            problem(call(customer,"POST","/api/orders",Map.of("quoteId",quote.get("id").asText(),"checkoutKey",key)),409,"DATA_CONFLICT");
            assertThat(get(employee,stock(product)).get("reserved").asInt()).isZero();
            assertThat(get(customer,"/api/cart").get("totalQuantity").asLong()).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from customer_order where checkout_key=?",Integer.class,key)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE customer_order DROP CONSTRAINT test_checkout_failure");
        }
        expect(call(customer,"POST","/api/orders",Map.of("quoteId",quote.get("id").asText(),"checkoutKey",key)),201);
        assertThat(get(employee,stock(product)).get("reserved").asInt()).isEqualTo(2);
    }

    @Test
    void successfulPaymentUsesFrozenAmountAndSellsReservedStockExactlyOnce() throws Exception {
        String product=offer(details(brand(),"Payment success"),"100.00",8);
        var order=createCustomerOrder(product,3);
        String orderPath="/api/orders/"+order.get("id").asText();
        String path=orderPath+"/payments";
        setPrice(product,"999.00");
        var input=Map.of("requestKey",UUID.randomUUID());
        var created=call(customer,"POST",path,input);
        var payment=expect(created,201);
        assertThat(payment.get("amountGross").decimalValue()).isEqualByComparingTo("320.00");
        assertThat(payment.get("simulation").asBoolean()).isTrue();
        String paymentPath=created.headers().firstValue("location").orElseThrow();
        assertThat(get(customer,paymentPath)).isEqualTo(payment);
        var results=parallel(List.of(
                request(customer,"POST",paymentPath+"/simulation-result",Map.of("outcome","SUCCEEDED")),
                request(customer,"POST",paymentPath+"/simulation-result",Map.of("outcome","SUCCEEDED"))));
        var paid=expect(results.getFirst(),200);
        assertThat(expect(results.getLast(),200)).isEqualTo(paid);
        assertThat(paid.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(get(customer,orderPath).get("status").asText()).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select paid_at from customer_order where id=?",java.sql.Timestamp.class,UUID.fromString(order.get("id").asText()))).isNotNull();
        assertThat(get(employee,stock(product)).get("onHand").asInt()).isEqualTo(5);
        assertThat(get(employee,stock(product)).get("reserved").asInt()).isZero();
        var history=get(employee,stock(product)+"/movements");
        assertThat(history.get("totalElements").asInt()).isEqualTo(2);
        assertThat(history.at("/items/0/type").asText()).isEqualTo("SALE");
        assertThat(history.at("/items/0/actorAccountId").isNull()).isTrue();
        assertThat(history.at("/items/0/orderId").asText()).isEqualTo(order.get("id").asText());
        assertThat(expect(call(customer,"POST",path,input),201)).isEqualTo(paid);
        problem(call(customer,"POST",paymentPath+"/simulation-result",Map.of("outcome","FAILED")),409,"PAYMENT_RESULT_CONFLICT");
        problem(call(customer,"POST",path,Map.of("requestKey",UUID.randomUUID())),409,"ORDER_NOT_PAYABLE");
        problem(call(customer,"POST",orderPath+"/cancellation",null),409,"ORDER_NOT_CANCELLABLE");
    }

    @Test
    void failedAndCancelledAttemptsAllowRetryWithoutExtendingReservation() throws Exception {
        String product=offer(details(brand(),"Retry payment"),"20.00",5);
        var order=createCustomerOrder(product,2);
        String orderPath="/api/orders/"+order.get("id").asText();
        String path=orderPath+"/payments";
        for (String outcome:List.of("FAILED","CANCELLED")) {
            var key=Map.of("requestKey",UUID.randomUUID());
            var responses=parallel(List.of(request(customer,"POST",path,key),request(customer,"POST",path,key)));
            var attempt=expect(responses.getFirst(),201);
            assertThat(expect(responses.getLast(),201)).isEqualTo(attempt);
            problem(call(customer,"POST",path,Map.of("requestKey",UUID.randomUUID())),409,"PAYMENT_PENDING");
            String result=path+"/"+attempt.get("id").asText()+"/simulation-result";
            var finished=expect(call(customer,"POST",result,Map.of("outcome",outcome)),200);
            assertThat(expect(call(customer,"POST",result,Map.of("outcome",outcome)),200)).isEqualTo(finished);
            assertThat(get(customer,orderPath).get("reservationExpiresAt")).isEqualTo(order.get("reservationExpiresAt"));
            assertThat(get(customer,orderPath).get("status").asText()).isEqualTo("PENDING_PAYMENT");
            assertThat(get(employee,stock(product)).get("reserved").asInt()).isEqualTo(2);
            assertThat(get(employee,stock(product)).get("onHand").asInt()).isEqualTo(5);
        }
        assertThat(get(customer,path+"?size=1").get("totalElements").asInt()).isEqualTo(2);
        assertThat(get(customer,path+"?size=1").get("totalPages").asInt()).isEqualTo(2);
        var next=expect(call(customer,"POST",path,Map.of("requestKey",UUID.randomUUID())),201);
        expect(call(customer,"POST",path+"/"+next.get("id").asText()+"/simulation-result",Map.of("outcome","SUCCEEDED")),200);
    }

    @Test
    void expiredSuccessCommitsCancellationAndReleasesStockWithoutBackgroundJob() throws Exception {
        String product=offer(details(brand(),"Late payment"),"20.00",5);
        var order=createCustomerOrder(product,2);
        UUID id=UUID.fromString(order.get("id").asText());
        String path="/api/orders/"+id+"/payments";
        var attempt=expect(call(customer,"POST",path,Map.of("requestKey",UUID.randomUUID())),201);
        jdbc.update("update customer_order set created_at=now()-interval '16 minutes',reservation_expires_at=now()-interval '1 minute' where id=?",id);
        problem(call(customer,"POST",path+"/"+attempt.get("id").asText()+"/simulation-result",Map.of("outcome","SUCCEEDED")),409,"ORDER_NOT_PAYABLE");
        assertThat(jdbc.queryForObject("select status from customer_order where id=?",String.class,id)).isEqualTo("CANCELLED");
        var cancelled=get(customer,path+"/"+attempt.get("id").asText());
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("reason").asText()).isEqualTo("EXPIRED");
        orders.expire(id);
        assertThat(get(employee,stock(product)).get("reserved").asInt()).isZero();
        assertThat(get(employee,stock(product)).get("onHand").asInt()).isEqualTo(5);
    }

    @Test
    void paymentAndOrderCancellationRaceHasOneAtomicOutcome() throws Exception {
        String product=offer(details(brand(),"Race cancellation"),"20.00",5);
        var order=createCustomerOrder(product,2);
        String orderPath="/api/orders/"+order.get("id").asText();
        String path=orderPath+"/payments";
        var attempt=expect(call(customer,"POST",path,Map.of("requestKey",UUID.randomUUID())),201);
        var responses=parallel(List.of(
                request(customer,"POST",path+"/"+attempt.get("id").asText()+"/simulation-result",Map.of("outcome","SUCCEEDED")),
                request(customer,"POST",orderPath+"/cancellation",null)));
        assertThat(responses.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(200,409);
        boolean paid=get(customer,orderPath).get("status").asText().equals("PAID");
        assertThat(get(customer,path+"/"+attempt.get("id").asText()).get("status").asText()).isEqualTo(paid?"SUCCEEDED":"CANCELLED");
        assertThat(get(employee,stock(product)).get("reserved").asInt()).isZero();
        assertThat(get(employee,stock(product)).get("onHand").asInt()).isEqualTo(paid?3:5);
        assertThat(get(employee,stock(product)+"/movements").get("totalElements").asInt()).isEqualTo(paid?2:1);
    }

    @Test
    void paymentWriteFailureRollsBackOrderSaleAndAuditTogether() throws Exception {
        String product=offer(details(brand(),"Rollback payment"),"20.00",5);
        var order=createCustomerOrder(product,2);
        String orderPath="/api/orders/"+order.get("id").asText();
        String path=orderPath+"/payments";
        var attempt=expect(call(customer,"POST",path,Map.of("requestKey",UUID.randomUUID())),201);
        UUID id=UUID.fromString(attempt.get("id").asText());
        String result=path+"/"+id+"/simulation-result";
        jdbc.execute("ALTER TABLE payment_attempt ADD CONSTRAINT test_payment_failure CHECK (id <> '"+id+"'::uuid OR status <> 'SUCCEEDED')");
        try {
            problem(call(customer,"POST",result,Map.of("outcome","SUCCEEDED")),409,"DATA_CONFLICT");
            assertThat(get(customer,orderPath).get("status").asText()).isEqualTo("PENDING_PAYMENT");
            assertThat(get(customer,path+"/"+id).get("status").asText()).isEqualTo("PENDING");
            assertThat(get(employee,stock(product)).get("onHand").asInt()).isEqualTo(5);
            assertThat(get(employee,stock(product)).get("reserved").asInt()).isEqualTo(2);
            assertThat(get(employee,stock(product)+"/movements").get("totalElements").asInt()).isEqualTo(1);
        } finally {
            jdbc.execute("ALTER TABLE payment_attempt DROP CONSTRAINT test_payment_failure");
        }
        expect(call(customer,"POST",result,Map.of("outcome","SUCCEEDED")),200);
    }

    @Test
    void paymentsEnforceOwnershipRolesInputAndAmountConsistency() throws Exception {
        String product=offer(details(brand(),"Payment privacy"),"20.00",5);
        var order=createCustomerOrder(product,2);
        String path="/api/orders/"+order.get("id").asText()+"/payments";
        var attempt=expect(call(customer,"POST",path,Map.of("requestKey",UUID.randomUUID())),201);
        String id=attempt.get("id").asText();
        String result=path+"/"+id+"/simulation-result";
        problem(call(null,"GET",path,null),401,"AUTHENTICATION_REQUIRED");
        problem(call(loginFixture("CUSTOMER"),"POST",result,Map.of("outcome","SUCCEEDED")),404,"ORDER_NOT_FOUND");
        for (String token:List.of(admin,employee)) problem(call(token,"POST",result,Map.of("outcome","SUCCEEDED")),403,"ACCESS_DENIED");
        problem(call(customer,"GET",path+"/"+UUID.randomUUID(),null),404,"PAYMENT_NOT_FOUND");
        problem(call(customer,"GET",path+"?size=101",null),400,"VALIDATION_FAILED");
        problem(call(customer,"POST",result,Map.of("outcome","PENDING")),400,"INVALID_REQUEST");
        problem(call(customer,"POST",result,Map.of("outcome","SUCCEEDED","amountGross",1)),400,"INVALID_REQUEST");
        problem(call(customer,"POST",path,Map.of("requestKey",UUID.randomUUID(),"accountId",UUID.randomUUID())),400,"INVALID_REQUEST");
        var manual=new HashMap<>(movement(-2)); manual.put("type","SALE");
        problem(call(employee,"POST",stock(product)+"/movements",manual),400,"INVALID_STOCK_CHANGE");
        jdbc.update("update payment_attempt set amount_gross=1 where id=?",UUID.fromString(id));
        problem(call(customer,"POST",result,Map.of("outcome","SUCCEEDED")),409,"PAYMENT_AMOUNT_MISMATCH");
        assertThat(get(employee,stock(product)).get("reserved").asInt()).isEqualTo(2);
        assertThat(get(employee,stock(product)).get("onHand").asInt()).isEqualTo(5);
        var api=get(null,"/v3/api-docs");
        assertThat(api.at("/paths/~1api~1orders~1{orderId}~1payments/post/security/0/bearerAuth").isArray()).isTrue();
        assertThat(api.at("/paths/~1api~1guest~1orders~1{orderId}~1payments/post/security/0/purchaseToken").isArray()).isTrue();
    }

    @Test
    void guestPaymentUsesOnlyPurchaseContextAndDoesNotBecomeAccountOrder() throws Exception {
        String product=offer(details(brand(),"Guest payment"),"20.00",5);
        String cart=newGuest();
        expect(guestCall(cart,"PUT","/items/"+product,Map.of("quantity",2)),200);
        var quote=expect(headerCall(null,"X-Cart-Token",cart,"POST","/api/guest/orders/preview",previewInput()),200);
        String token=quote.get("purchaseToken").asText();
        var order=expect(headerCall(null,"X-Purchase-Token",token,"POST","/api/guest/orders",
                Map.of("quoteId",quote.get("id").asText(),"checkoutKey",UUID.randomUUID())),201);
        String path="/api/guest/orders/"+order.get("id").asText()+"/payments";
        problem(headerCall(null,"X-Purchase-Token",cart,"POST",path,Map.of("requestKey",UUID.randomUUID())),404,"ORDER_NOT_FOUND");
        var attempt=expect(headerCall(null,"X-Purchase-Token",token,"POST",path,Map.of("requestKey",UUID.randomUUID())),201);
        expect(headerCall(customer,"X-Purchase-Token",token,"POST",path+"/"+attempt.get("id").asText()+"/simulation-result",Map.of("outcome","SUCCEEDED")),200);
        assertThat(expect(headerCall(null,"X-Purchase-Token",token,"GET","/api/guest/orders/"+order.get("id").asText(),null),200).get("status").asText()).isEqualTo("PAID");
        problem(call(customer,"GET","/api/orders/"+order.get("id").asText()+"/payments",null),404,"ORDER_NOT_FOUND");
    }

    @Test
    void expiryRacingWithPaymentClosesPendingAttemptAndReleasesOnlyOnce() throws Exception {
        String product=offer(details(brand(),"Expiry race"),"20.00",5);
        var order=createCustomerOrder(product,2);
        UUID id=UUID.fromString(order.get("id").asText());
        String path="/api/orders/"+id+"/payments";
        var attempt=expect(call(customer,"POST",path,Map.of("requestKey",UUID.randomUUID())),201);
        jdbc.update("update customer_order set created_at=now()-interval '16 minutes',reservation_expires_at=now()-interval '1 minute' where id=?",id);
        var expiry=CompletableFuture.runAsync(()->orders.expire(id));
        expect(call(customer,"POST",path+"/"+attempt.get("id").asText()+"/simulation-result",Map.of("outcome","SUCCEEDED")),409);
        expiry.get(20,TimeUnit.SECONDS);
        assertThat(get(customer,path+"/"+attempt.get("id").asText()).get("reason").asText()).isEqualTo("EXPIRED");
        assertThat(get(employee,stock(product)).get("onHand").asInt()).isEqualTo(5);
        assertThat(get(employee,stock(product)).get("reserved").asInt()).isZero();
        assertThat(get(employee,stock(product)+"/movements").get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void multiProductSalePreservesAnotherOrdersReservationDuringStockReceipt() throws Exception {
        String first=offer(details(brand(),"First sale item"),"20.00",5);
        String second=offer(details(brand(),"Second sale item"),"30.00",5);
        createCustomerOrder(first,1);
        expect(call(customer,"PUT","/api/cart/items/"+first,Map.of("quantity",2)),200);
        var order=createCustomerOrder(second,2);
        String path="/api/orders/"+order.get("id").asText()+"/payments";
        var attempt=expect(call(customer,"POST",path,Map.of("requestKey",UUID.randomUUID())),201);
        assertThat(attempt.get("amountGross").decimalValue()).isEqualByComparingTo("100.00");
        var results=parallel(List.of(
                request(customer,"POST",path+"/"+attempt.get("id").asText()+"/simulation-result",Map.of("outcome","SUCCEEDED")),
                request(employee,"POST",stock(first)+"/movements",movement(1))));
        for (var response:results) expect(response,200);
        assertThat(get(employee,stock(first)).get("onHand").asInt()).isEqualTo(4);
        assertThat(get(employee,stock(first)).get("reserved").asInt()).isEqualTo(1);
        assertThat(get(employee,stock(second)).get("onHand").asInt()).isEqualTo(3);
        assertThat(get(employee,stock(second)).get("reserved").asInt()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from inventory_change where order_id=?",Integer.class,UUID.fromString(order.get("id").asText()))).isEqualTo(2);
    }

    @Test
    void paidGuestGetsOnePrivateTrackingLinkAndOneEncryptedMailJob() throws Exception {
        var guest=guestPurchase();
        String confirmation="/api/guest/orders/"+guest.id()+"/tracking-link";
        problem(headerCall(null,"X-Purchase-Token",guest.purchase(),"GET",confirmation,null),409,"ORDER_NOT_PAID");
        org.mockito.Mockito.verifyNoInteractions(mail);
        pay(guest);
        var response=headerCall(null,"X-Purchase-Token",guest.purchase(),"GET",confirmation,null);
        var link=expect(response,200);
        assertThat(response.headers().firstValue("cache-control").orElseThrow()).contains("no-store");
        String raw=trackingToken(link);
        assertThat(raw).hasSize(43).isNotEqualTo(guest.purchase()).isNotEqualTo(guest.cart());
        assertThat(link.get("trackingUrl").asText()).startsWith("http://localhost:4200/track-order#token=");
        var expires=java.time.Instant.parse(link.get("expiresAt").asText());
        var paidAt=jdbc.queryForObject("select paid_at from customer_order where id=?",java.sql.Timestamp.class,guest.id()).toInstant();
        assertThat(expires).isEqualTo(paidAt.plus(Duration.ofDays(90)));
        var tracked=expect(track(raw),200);
        assertThat(tracked.size()).isEqualTo(3);
        assertThat(tracked.get("orderNumber").asText()).isEqualTo("TS-"+guest.id());
        assertThat(tracked.get("status").asText()).isEqualTo("PAID");
        assertThat(tracked.get("trackingNumber").isNull()).isTrue();
        assertThat(tracked.toString()).doesNotContain("email","address","phone","snapshot");
        var tokenId=jdbc.queryForObject("select id from order_access_token where order_id=?",UUID.class,guest.id());
        assertThat(jdbc.queryForObject("select token_hash from order_access_token where id=?",String.class,tokenId))
                .isEqualTo(com.shop.tire.shared.security.OpaqueTokens.hash(raw));
        assertThat(new String(jdbc.queryForObject("select screen_ciphertext from order_access_token where id=?",byte[].class,tokenId),java.nio.charset.StandardCharsets.UTF_8)).doesNotContain(raw);
        assertThat(new String(jdbc.queryForObject("select payload from order_email_outbox where id=?",byte[].class,tokenId),java.nio.charset.StandardCharsets.UTF_8)).doesNotContain(raw,"order@example.test");
        org.mockito.Mockito.verifyNoInteractions(mail);
        assertThat(delivery.sendNext()).isTrue();
        var message=org.mockito.ArgumentCaptor.forClass(org.springframework.mail.SimpleMailMessage.class);
        org.mockito.Mockito.verify(mail).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("order@example.test");
        assertThat(message.getValue().getText()).contains(link.get("trackingUrl").asText());
        assertThat(jdbc.queryForObject("select payload from order_email_outbox where id=?",byte[].class,tokenId)).isNull();
        pay(guest);
        assertThat(expect(headerCall(customer,"X-Purchase-Token",guest.purchase(),"GET",confirmation,null),200)).isEqualTo(link);
        assertThat(jdbc.queryForObject("select count(*) from order_access_token where order_id=?",Integer.class,guest.id())).isEqualTo(1);
        assertThat(delivery.sendNext()).isFalse();
        jdbc.update("update customer_order set status='SHIPPED',tracking_number='TEST-123' where id=?",guest.id());
        assertThat(expect(track(raw),200).get("trackingNumber").asText()).isEqualTo("TEST-123");
        assertThat(expect(track(raw),200).get("status").asText()).isEqualTo("SHIPPED");
    }

    @Test
    void trackingTokenCannotPayCancelOrRevealPurchaseAndExpiresIndependently() throws Exception {
        var guest=guestPurchase();
        pay(guest);
        String path="/api/guest/orders/"+guest.id()+"/tracking-link";
        String raw=trackingToken(expect(headerCall(null,"X-Purchase-Token",guest.purchase(),"GET",path,null),200));
        problem(track(guest.purchase()),404,"TRACKING_NOT_FOUND");
        problem(track(guest.cart()),404,"TRACKING_NOT_FOUND");
        problem(call(null,"GET","/api/order-tracking",null),404,"TRACKING_NOT_FOUND");
        problem(call(null,"GET","/api/order-tracking?token="+raw,null),404,"TRACKING_NOT_FOUND");
        problem(headerCall(null,"X-Purchase-Token",raw,"GET","/api/guest/orders/"+guest.id(),null),404,"ORDER_NOT_FOUND");
        problem(headerCall(null,"X-Purchase-Token",raw,"POST","/api/guest/orders/"+guest.id()+"/cancellation",null),404,"ORDER_NOT_FOUND");
        problem(headerCall(null,"X-Purchase-Token",raw,"POST","/api/guest/orders/"+guest.id()+"/payments",Map.of("requestKey",UUID.randomUUID())),404,"ORDER_NOT_FOUND");
        problem(call(customer,"GET",path,null),404,"ORDER_NOT_FOUND");
        jdbc.update("update customer_order set access_expires_at=? where id=?",java.sql.Timestamp.from(clock.instant().minusSeconds(1)),guest.id());
        problem(headerCall(null,"X-Purchase-Token",guest.purchase(),"GET",path,null),404,"ORDER_NOT_FOUND");
        expect(track(raw),200);
        jdbc.update("update order_access_token set screen_expires_at=? where order_id=?",java.sql.Timestamp.from(clock.instant().minusSeconds(1)),guest.id());
        delivery.cleanExpiredSecrets();
        assertThat(jdbc.queryForObject("select screen_ciphertext from order_access_token where order_id=?",byte[].class,guest.id())).isNull();
        // Eligibility uses the application Clock, which need not match the Docker VM's clock.
        jdbc.update("update order_access_token set created_at=?,expires_at=? where order_id=?",
                java.sql.Timestamp.from(clock.instant().minus(Duration.ofDays(91))),
                java.sql.Timestamp.from(clock.instant().minusSeconds(1)),guest.id());
        problem(track(raw),404,"TRACKING_NOT_FOUND");
    }

    @Test
    void renewalIsPrivateThrottledConcurrentAndDoesNotRevokeExistingLink() throws Exception {
        var guest=guestPurchase();
        pay(guest);
        var original=expect(headerCall(null,"X-Purchase-Token",guest.purchase(),"GET","/api/guest/orders/"+guest.id()+"/tracking-link",null),200);
        var body=Map.of("orderNumber","TS-"+guest.id(),"email"," ORDER@example.test ");
        var requests=List.of(request(null,"POST","/api/order-tracking/link-requests",body),request(null,"POST","/api/order-tracking/link-requests",body));
        var results=parallel(requests);
        var accepted=expect(results.getFirst(),202);
        assertThat(expect(results.getLast(),202)).isEqualTo(accepted);
        assertThat(accepted.toString()).doesNotContain("token","trackingUrl",guest.id().toString());
        assertThat(jdbc.queryForObject("select count(*) from order_access_token where order_id=?",Integer.class,guest.id())).isEqualTo(2);
        assertThat(expect(call(null,"POST","/api/order-tracking/link-requests",Map.of("orderNumber","TS-"+UUID.randomUUID(),"email","missing@example.test")),202)).isEqualTo(accepted);
        jdbc.update("delete from tracking_request_limit");
        assertThat(expect(call(null,"POST","/api/order-tracking/link-requests",Map.of("orderNumber","TS-"+guest.id(),"email","other@example.test")),202)).isEqualTo(accepted);
        assertThat(jdbc.queryForObject("select count(*) from order_access_token where order_id=?",Integer.class,guest.id())).isEqualTo(2);
        expect(track(trackingToken(original)),200);
        assertThat(delivery.sendNext()).isTrue();
        assertThat(delivery.sendNext()).isTrue();
        var messages=org.mockito.ArgumentCaptor.forClass(org.springframework.mail.SimpleMailMessage.class);
        org.mockito.Mockito.verify(mail,org.mockito.Mockito.times(2)).send(messages.capture());
        for (var message:messages.getAllValues()) assertThat(message.getTo()).containsExactly("order@example.test");
        var newMessage=messages.getAllValues().stream().filter(m->!m.getText().contains(trackingToken(original))).findFirst().orElseThrow();
        String renewed=newMessage.getText().split("#token=")[1].substring(0,43);
        expect(track(renewed),200);
        assertThat(jdbc.queryForObject("select expires_at-created_at from order_access_token where order_id=? and not initial",Object.class,guest.id()).toString()).contains("90");
        assertThat(jdbc.queryForObject("select screen_ciphertext from order_access_token where order_id=? and not initial",byte[].class,guest.id())).isNull();
        problem(call(null,"POST","/api/order-tracking/link-requests",Map.of("orderNumber","TS-"+guest.id(),"email","order@example.test","recipient","attacker@example.test")),400,"INVALID_REQUEST");
    }

    @Test
    void publicRenewalLimitsNetworkPeerEvenWithSpoofedForwardedFor() throws Exception {
        for (int i=0;i<10;i++) expect(headerCall(null,"X-Forwarded-For","192.0.2."+i,"POST","/api/order-tracking/link-requests",
                Map.of("orderNumber","TS-"+UUID.randomUUID(),"email","missing@example.test")),202);
        var guest=guestPurchase();
        pay(guest);
        expect(headerCall(null,"X-Forwarded-For","192.0.2.99","POST","/api/order-tracking/link-requests",
                Map.of("orderNumber","TS-"+guest.id(),"email","order@example.test")),202);
        assertThat(jdbc.queryForObject("select count(*) from order_access_token where order_id=?",Integer.class,guest.id())).isEqualTo(1);
    }

    @Test
    void mailFailureRetriesAfterCommitAndClearsPayloadAfterSuccess() throws Exception {
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("SMTP unavailable"))
                .when(mail).send(org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));
        var guest=guestPurchase();
        pay(guest);
        assertThat(jdbc.queryForObject("select status from customer_order where id=?",String.class,guest.id())).isEqualTo("PAID");
        UUID job=jdbc.queryForObject("select id from order_access_token where order_id=?",UUID.class,guest.id());
        assertThat(delivery.sendNext()).isTrue();
        assertThat(jdbc.queryForObject("select attempts from order_email_outbox where id=?",Integer.class,job)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from order_email_outbox where id=?",String.class,job)).isEqualTo("PENDING");
        assertThat(delivery.sendNext()).isFalse();
        org.mockito.Mockito.doNothing().when(mail).send(org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));
        jdbc.update("update order_email_outbox set next_attempt_at=? where id=?",java.sql.Timestamp.from(clock.instant().minusSeconds(1)),job);
        var one=CompletableFuture.supplyAsync(()->delivery.sendNext());
        var two=CompletableFuture.supplyAsync(()->delivery.sendNext());
        assertThat(List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        assertThat(jdbc.queryForObject("select status from order_email_outbox where id=?",String.class,job)).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("select payload from order_email_outbox where id=?",byte[].class,job)).isNull();
        org.mockito.Mockito.verify(mail,org.mockito.Mockito.times(2)).send(org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));
        assertThat(jdbc.queryForObject("select status from customer_order where id=?",String.class,guest.id())).isEqualTo("PAID");
    }

    @Test
    void exhaustedMailRetriesRemoveEncryptedPayloadWithoutUndoingPayment() throws Exception {
        var guest=guestPurchase();
        pay(guest);
        UUID job=jdbc.queryForObject("select id from order_access_token where order_id=?",UUID.class,guest.id());
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("SMTP unavailable"))
                .when(mail).send(org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));
        for (int i=0;i<5;i++) {
            jdbc.update("update order_email_outbox set next_attempt_at=? where id=?",java.sql.Timestamp.from(clock.instant().minusSeconds(1)),job);
            assertThat(delivery.sendNext()).isTrue();
        }
        assertThat(jdbc.queryForObject("select status from order_email_outbox where id=?",String.class,job)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select payload from order_email_outbox where id=?",byte[].class,job)).isNull();
        assertThat(delivery.sendNext()).isFalse();
        assertThat(jdbc.queryForObject("select status from customer_order where id=?",String.class,guest.id())).isEqualTo("PAID");
    }

    @Test
    void failureAfterPreparingGuestMailRollsBackLinkAndMessageWithPayment() throws Exception {
        var guest=guestPurchase();
        jdbc.execute("ALTER TABLE payment_attempt ADD CONSTRAINT test_guest_payment_failure CHECK (id <> '"+guest.payment()+"'::uuid OR status <> 'SUCCEEDED')");
        try {
            expect(headerCall(null,"X-Purchase-Token",guest.purchase(),"POST",guest.resultPath(),Map.of("outcome","SUCCEEDED")),409);
            assertThat(jdbc.queryForObject("select count(*) from order_access_token where order_id=?",Integer.class,guest.id())).isZero();
            assertThat(jdbc.queryForObject("select status from customer_order where id=?",String.class,guest.id())).isEqualTo("PENDING_PAYMENT");
            assertThat(delivery.sendNext()).isFalse();
            org.mockito.Mockito.verifyNoInteractions(mail);
        } finally {
            jdbc.execute("ALTER TABLE payment_attempt DROP CONSTRAINT test_guest_payment_failure");
        }
        pay(guest);
        assertThat(jdbc.queryForObject("select count(*) from order_access_token where order_id=?",Integer.class,guest.id())).isEqualTo(1);
    }

    @Test
    void trackingOpenApiCorsAndInvalidRequestsMatchContract() throws Exception {
        var api=get(null,"/v3/api-docs");
        assertThat(api.at("/paths/~1api~1order-tracking/get/security/0/trackingToken").isArray()).isTrue();
        assertThat(api.at("/paths/~1api~1order-tracking~1link-requests/post/security").isEmpty()).isTrue();
        assertThat(api.at("/paths/~1api~1guest~1orders~1{id}~1tracking-link/get/security/0/purchaseToken").isArray()).isTrue();
        problem(call(null,"POST","/api/order-tracking/link-requests",Map.of("orderNumber","bad","email","bad")),400,"VALIDATION_FAILED");
        var preflight=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/order-tracking"))
                .header("Origin","http://localhost:4200").header("Access-Control-Request-Method","GET")
                .header("Access-Control-Request-Headers","X-Tracking-Token").method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build();
        var response=http.send(preflight,HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("access-control-allow-headers").orElseThrow()).containsIgnoringCase("X-Tracking-Token");
    }

    private record GuestPurchase(UUID id, String cart, String purchase, UUID payment) {
        String resultPath() { return "/api/guest/orders/"+id+"/payments/"+payment+"/simulation-result"; }
    }
    private GuestPurchase guestPurchase() throws Exception {
        String product=offer(details(brand(),"Tracking"),"20.00",5);
        String cart=newGuest();
        expect(guestCall(cart,"PUT","/items/"+product,Map.of("quantity",2)),200);
        var quote=expect(headerCall(null,"X-Cart-Token",cart,"POST","/api/guest/orders/preview",previewInput()),200);
        String purchase=quote.get("purchaseToken").asText();
        var order=expect(headerCall(null,"X-Purchase-Token",purchase,"POST","/api/guest/orders",
                Map.of("quoteId",quote.get("id").asText(),"checkoutKey",UUID.randomUUID())),201);
        UUID id=UUID.fromString(order.get("id").asText());
        var payment=expect(headerCall(null,"X-Purchase-Token",purchase,"POST","/api/guest/orders/"+id+"/payments",
                Map.of("requestKey",UUID.randomUUID())),201);
        return new GuestPurchase(id,cart,purchase,UUID.fromString(payment.get("id").asText()));
    }
    private void pay(GuestPurchase guest) throws Exception {
        expect(headerCall(null,"X-Purchase-Token",guest.purchase(),"POST",guest.resultPath(),Map.of("outcome","SUCCEEDED")),200);
    }
    private String trackingToken(JsonNode link) { return link.get("trackingUrl").asText().split("#token=")[1]; }
    private HttpResponse<String> track(String token) throws Exception {
        return headerCall(null,"X-Tracking-Token",token,"GET","/api/order-tracking",null);
    }

    @Test
    void employeeFulfillsPaidCustomerOrderAndCustomerSeesTrackingNumber() throws Exception {
        String product=offer(details(brand(),"Fulfillment"),"25.00",5);
        var order=createCustomerOrder(product,2);
        String id=order.get("id").asText();
        payCustomerOrder(id);
        String path="/api/employee/orders/"+id;
        var processing=statusChange("PROCESSING",null);
        var started=expect(call(employee,"PATCH",path+"/status",processing),200);
        assertThat(started.get("status").asText()).isEqualTo("PROCESSING");
        var shipped=expect(call(employee,"PATCH",path+"/status",statusChange("SHIPPED","PARCEL-123")),200);
        assertThat(shipped.get("trackingNumber").asText()).isEqualTo("PARCEL-123");
        var details=get(employee,path);
        assertThat(details.at("/snapshot/delivery/email").asText()).isEqualTo("order@example.test");
        assertThat(details.at("/snapshot/items")).isEqualTo(order.at("/snapshot/items"));
        assertThat(details.toString()).doesNotContain("guestHash","token","password","accountId");
        assertThat(get(customer,"/api/orders/"+id).get("trackingNumber").asText()).isEqualTo("PARCEL-123");
        assertThat(get(customer,"/api/orders").at("/items/0/trackingNumber").asText()).isEqualTo("PARCEL-123");
        expect(call(employee,"PATCH",path+"/status",statusChange("DELIVERED",null)),200);
        assertThat(expect(call(employee,"PATCH",path+"/status",processing),200)).isEqualTo(started);
        assertThat(get(employee,path).get("status").asText()).isEqualTo("DELIVERED");
        assertThat(get(employee,stock(product)).get("onHand").asInt()).isEqualTo(3);
        assertThat(get(employee,stock(product)).get("reserved").asInt()).isZero();
        assertThat(get(employee,stock(product)+"/movements").get("totalElements").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from order_fulfillment_operation where order_id=?",Integer.class,UUID.fromString(id))).isEqualTo(3);
    }

    @Test
    void guestTrackingFollowsShipmentCorrectionsAndDelivery() throws Exception {
        var guest=guestPurchase();
        pay(guest);
        String token=trackingToken(expect(headerCall(null,"X-Purchase-Token",guest.purchase(),"GET","/api/guest/orders/"+guest.id()+"/tracking-link",null),200));
        String path="/api/employee/orders/"+guest.id();
        expect(call(employee,"PATCH",path+"/status",statusChange("PROCESSING",null)),200);
        expect(call(employee,"PATCH",path+"/status",statusChange("SHIPPED","OLD-123")),200);
        var correction=Map.of("operationId",UUID.randomUUID(),"trackingNumber","NEW-456","expectedTrackingNumber","OLD-123");
        var corrected=expect(call(employee,"PUT",path+"/shipment",correction),200);
        assertThat(expect(track(token),200).get("trackingNumber").asText()).isEqualTo("NEW-456");
        problem(call(employee,"PUT",path+"/shipment",Map.of("operationId",UUID.randomUUID(),"trackingNumber","STALE-123","expectedTrackingNumber","OLD-123")),409,"SHIPMENT_CHANGED");
        expect(call(employee,"PATCH",path+"/status",statusChange("DELIVERED",null)),200);
        assertThat(expect(track(token),200).get("status").asText()).isEqualTo("DELIVERED");
        assertThat(expect(call(employee,"PUT",path+"/shipment",correction),200)).isEqualTo(corrected);
        problem(call(employee,"PUT",path+"/shipment",Map.of("operationId",UUID.randomUUID(),"trackingNumber","TOO-LATE","expectedTrackingNumber","NEW-456")),409,"SHIPMENT_NOT_EDITABLE");
        pay(guest);
        assertThat(expect(track(token),200).get("status").asText()).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject("select count(*) from order_access_token where order_id=?",Integer.class,guest.id())).isEqualTo(1);
    }

    @Test
    void fulfillmentRejectsSkippedStagesUnpaidCancelledAndForgedFields() throws Exception {
        String product=offer(details(brand(),"Fulfillment validation"),"25.00",8);
        var order=createCustomerOrder(product,2);
        String id=order.get("id").asText();
        String path="/api/employee/orders/"+id;
        problem(call(employee,"PATCH",path+"/status",statusChange("PROCESSING",null)),409,"ORDER_TRANSITION_NOT_ALLOWED");
        problem(call(employee,"PUT",path+"/shipment",Map.of("operationId",UUID.randomUUID(),"trackingNumber","NEW","expectedTrackingNumber","OLD")),409,"SHIPMENT_NOT_EDITABLE");
        for (String target:List.of("PAID","CANCELLED","PENDING_PAYMENT"))
            problem(call(employee,"PATCH",path+"/status",statusChange(target,null)),400,"INVALID_REQUEST");
        var forged=new HashMap<>(statusChange("PROCESSING",null)); forged.put("actorAccountId",UUID.randomUUID());
        problem(call(employee,"PATCH",path+"/status",forged),400,"INVALID_REQUEST");
        payCustomerOrder(id);
        problem(call(employee,"PATCH",path+"/status",statusChange("DELIVERED",null)),409,"ORDER_TRANSITION_NOT_ALLOWED");
        problem(call(employee,"PATCH",path+"/status",statusChange("SHIPPED","PARCEL")),409,"ORDER_TRANSITION_NOT_ALLOWED");
        problem(call(employee,"PATCH",path+"/status",statusChange("PROCESSING","PARCEL")),400,"TRACKING_NUMBER_REQUIRED");
        expect(call(employee,"PATCH",path+"/status",statusChange("PROCESSING",null)),200);
        problem(call(employee,"PATCH",path+"/status",statusChange("SHIPPED",null)),400,"TRACKING_NUMBER_REQUIRED");
        problem(call(employee,"PATCH",path+"/status",statusChange("SHIPPED","  ")),400,"VALIDATION_FAILED");
        problem(call(employee,"PATCH",path+"/status",statusChange("SHIPPED","X".repeat(101))),400,"VALIDATION_FAILED");
        problem(call(employee,"PATCH",path+"/status",statusChange("SHIPPED","<script>")),400,"VALIDATION_FAILED");
        var cancelled=createCustomerOrder(product,1);
        String cancelledId=cancelled.get("id").asText();
        expect(call(customer,"POST","/api/orders/"+cancelledId+"/cancellation",null),200);
        problem(call(employee,"PATCH","/api/employee/orders/"+cancelledId+"/status",statusChange("PROCESSING",null)),409,"ORDER_TRANSITION_NOT_ALLOWED");
    }

    @Test
    void employeeOrdersKeepRolesPaginationAndPrivateResourcesSeparate() throws Exception {
        String product=offer(details(brand(),"Employee access"),"25.00",5);
        var order=createCustomerOrder(product,2);
        String id=order.get("id").asText();
        String path="/api/employee/orders/"+id;
        problem(call(null,"GET","/api/employee/orders",null),401,"AUTHENTICATION_REQUIRED");
        for (String token:List.of(admin,customer)) {
            problem(call(token,"GET","/api/employee/orders",null),403,"ACCESS_DENIED");
            problem(call(token,"GET",path,null),403,"ACCESS_DENIED");
            problem(call(token,"PATCH",path+"/status",statusChange("PROCESSING",null)),403,"ACCESS_DENIED");
            problem(call(token,"PUT",path+"/shipment",Map.of("operationId",UUID.randomUUID(),"trackingNumber","NEW","expectedTrackingNumber","OLD")),403,"ACCESS_DENIED");
        }
        problem(call(employee,"GET","/api/employee/orders/"+UUID.randomUUID(),null),404,"ORDER_NOT_FOUND");
        problem(call(employee,"GET","/api/employee/orders?size=101",null),400,"VALIDATION_FAILED");
        problem(call(employee,"GET","/api/employee/orders?page=-1",null),400,"VALIDATION_FAILED");
        problem(call(employee,"GET","/api/employee/orders?status=BAD",null),400,"INVALID_PARAMETER");
        problem(call(loginFixture("CUSTOMER"),"GET","/api/orders/"+id,null),404,"ORDER_NOT_FOUND");
        var api=get(null,"/v3/api-docs");
        assertThat(api.at("/paths/~1api~1employee~1orders~1{id}~1status/patch/security/0/bearerAuth").isArray()).isTrue();
    }

    @Test
    void concurrentFulfillmentRetriesAndCorrectionsDoNotDuplicateOrOverwrite() throws Exception {
        String product=offer(details(brand(),"Concurrent fulfillment"),"25.00",5);
        String id=createCustomerOrder(product,2).get("id").asText();
        payCustomerOrder(id);
        String path="/api/employee/orders/"+id;
        var input=statusChange("PROCESSING",null);
        var results=parallel(List.of(request(employee,"PATCH",path+"/status",input),request(employee,"PATCH",path+"/status",input)));
        var first=expect(results.getFirst(),200);
        assertThat(expect(results.getLast(),200)).isEqualTo(first);
        var changed=new HashMap<>(input); changed.put("status","DELIVERED");
        problem(call(employee,"PATCH",path+"/status",changed),409,"FULFILLMENT_OPERATION_REUSED");
        problem(call(loginFixture("EMPLOYEE"),"PATCH",path+"/status",input),409,"FULFILLMENT_OPERATION_REUSED");
        results=parallel(List.of(request(employee,"PATCH",path+"/status",statusChange("SHIPPED","ONE")),
                request(employee,"PATCH",path+"/status",statusChange("SHIPPED","TWO"))));
        assertThat(results.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(200,409);
        String original=get(employee,path).get("trackingNumber").asText();
        results=parallel(List.of(
                request(employee,"PUT",path+"/shipment",Map.of("operationId",UUID.randomUUID(),"expectedTrackingNumber",original,"trackingNumber","THREE")),
                request(employee,"PUT",path+"/shipment",Map.of("operationId",UUID.randomUUID(),"expectedTrackingNumber",original,"trackingNumber","FOUR"))));
        assertThat(results.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(200,409);
        assertThat(get(employee,path).get("trackingNumber").asText()).isIn("THREE","FOUR");
        assertThat(jdbc.queryForObject("select count(*) from order_fulfillment_operation where order_id=?",Integer.class,UUID.fromString(id))).isEqualTo(3);
    }

    @Test
    void fulfillmentWriteFailureRollsBackStatusAndShipmentTogether() throws Exception {
        String product=offer(details(brand(),"Fulfillment rollback"),"25.00",5);
        String id=createCustomerOrder(product,2).get("id").asText();
        payCustomerOrder(id);
        String path="/api/employee/orders/"+id;
        expect(call(employee,"PATCH",path+"/status",statusChange("PROCESSING",null)),200);
        var input=statusChange("SHIPPED","PARCEL");
        UUID key=(UUID)input.get("operationId");
        jdbc.execute("ALTER TABLE order_fulfillment_operation ADD CONSTRAINT test_fulfillment_failure CHECK (id <> '"+key+"'::uuid)");
        try {
            problem(call(employee,"PATCH",path+"/status",input),409,"DATA_CONFLICT");
            var state=get(employee,path);
            assertThat(state.get("status").asText()).isEqualTo("PROCESSING");
            assertThat(state.get("trackingNumber").isNull()).isTrue();
            assertThat(jdbc.queryForObject("select count(*) from order_fulfillment_operation where id=?",Integer.class,key)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE order_fulfillment_operation DROP CONSTRAINT test_fulfillment_failure");
        }
        expect(call(employee,"PATCH",path+"/status",input),200);
    }

    @Test
    void employeeListUsesStablePagesAndEffectiveExpiredStatusWithoutMutating() throws Exception {
        String product=offer(details(brand(),"Employee pages"),"25.00",5);
        String first=createCustomerOrder(product,1).get("id").asText();
        String second=createCustomerOrder(product,1).get("id").asText();
        for (String id:List.of(first,second))
            jdbc.update("update customer_order set created_at='2090-01-01T00:00:00Z',reservation_expires_at='2090-01-01T00:15:00Z' where id=?",UUID.fromString(id));
        var sorted=List.of(first,second).stream().sorted().toList();
        assertThat(get(employee,"/api/employee/orders?size=1&page=0").at("/items/0/id").asText()).isEqualTo(sorted.getFirst());
        assertThat(get(employee,"/api/employee/orders?size=1&page=1").at("/items/0/id").asText()).isEqualTo(sorted.getLast());
        assertThat(get(employee,"/api/employee/orders?size=1").at("/items/0").toString()).doesNotContain("delivery","snapshot","email");
        jdbc.update("update customer_order set created_at=now()-interval '16 minutes',reservation_expires_at=now()-interval '1 minute' where id=?",UUID.fromString(first));
        var cancelled=get(employee,"/api/employee/orders?status=CANCELLED&size=100");
        assertThat(ids(cancelled)).contains(first);
        for (var item:cancelled.get("items")) assertThat(item.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(get(employee,"/api/employee/orders/"+first).get("cancellationReason").asText()).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("select status from customer_order where id=?",String.class,UUID.fromString(first))).isEqualTo("PENDING_PAYMENT");
    }

    private Map<String,Object> statusChange(String status, String tracking) {
        var input=new HashMap<String,Object>();
        input.put("operationId",UUID.randomUUID()); input.put("status",status);
        if (tracking!=null) input.put("trackingNumber",tracking);
        return input;
    }
    private void payCustomerOrder(String id) throws Exception {
        String path="/api/orders/"+id+"/payments";
        var payment=expect(call(customer,"POST",path,Map.of("requestKey",UUID.randomUUID())),201);
        expect(call(customer,"POST",path+"/"+payment.get("id").asText()+"/simulation-result",Map.of("outcome","SUCCEEDED")),200);
    }

    private Map<String,Object> deliveryAddress() {
        return Map.of("recipientName","Test Customer","street","Testowa","buildingNumber","1","postalCode","00-001","city","Warszawa","countryCode","PL");
    }
    @Test
    void auditRecordsCreationAndCancellationExactlyOnceThroughHttp() throws Exception {
        String product = offer(details(brand(), "Audit"), "100.00", 5);
        expect(call(customer, "PUT", "/api/cart/items/" + product, Map.of("quantity", 1)), 200);
        var quote = preview(customer);
        var checkout = Map.of("quoteId", quote.get("id").asText(), "checkoutKey", UUID.randomUUID());
        setPrice(product, "101.00");
        problem(call(customer, "POST", "/api/orders", checkout), 409, "CHECKOUT_CHANGED");
        assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isZero();
        setPrice(product, "100.00");
        var created = expect(call(customer, "POST", "/api/orders", checkout), 201);
        expect(call(customer, "POST", "/api/orders", checkout), 201);
        String orderId = created.get("id").asText();
        expect(call(customer, "POST", "/api/orders/" + orderId + "/cancellation", null), 200);
        expect(call(customer, "POST", "/api/orders/" + orderId + "/cancellation", null), 200);

        var page = get(admin, "/api/admin/audit-log?size=1");
        assertThat(page.get("totalElements").asInt()).isEqualTo(2);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page.get("items").size()).isEqualTo(1);
        assertThat(page.at("/items/0/eventType").asText()).isEqualTo("ORDER_CANCELLED");
        assertThat(page.at("/items/0/payload/orderId").asText()).isEqualTo(orderId);
        assertThat(page.at("/items/0/payload/reason").asText()).isEqualTo("CUSTOMER");
        assertThat(page.at("/items/0/payload/actorAccountId").asText()).isEqualTo(
                jdbc.queryForObject("select account_id::text from customer_order where id=?", String.class, UUID.fromString(orderId)));
        assertThat(get(admin, "/api/admin/audit-log?size=1&page=1").at("/items/0/eventType").asText()).isEqualTo("ORDER_CREATED");
        var filtered = get(admin, "/api/admin/audit-log?eventType=ORDER_CREATED");
        assertThat(filtered.get("totalElements").asInt()).isEqualTo(1);
        assertThat(filtered.toString()).doesNotContain("order@example.test", "purchaseToken", "guestHash", "address");
    }

    @Test
    void auditEndpointRequiresAdminAndValidPagination() throws Exception {
        String path = "/api/admin/audit-log";
        problem(call(null, "GET", path, null), 401, "AUTHENTICATION_REQUIRED");
        problem(call(customer, "GET", path, null), 403, "ACCESS_DENIED");
        problem(call(employee, "GET", path, null), 403, "ACCESS_DENIED");
        for (String query : List.of("size=0", "size=101", "page=-1", "page=1000001")) {
            problem(call(admin, "GET", path + "?" + query, null), 400, "VALIDATION_FAILED");
        }
        problem(call(admin, "GET", path + "?eventType=unknown", null), 400, "INVALID_PARAMETER");
        var operation = get(null, "/v3/api-docs").path("paths").path(path).path("get");
        assertThat(operation.path("security").toString()).contains("bearerAuth");
        assertThat(operation.path("responses").has("400")).isTrue();
        assertThat(get(admin, path).get("totalElements").asInt()).isZero();
    }

    @Test
    void auditRunsOnlyAfterCommitAndIsDiscardedOnRollback() {
        UUID orderId = UUID.randomUUID();
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> {
            events.publishEvent(new com.shop.tire.order.event.OrderCreated(orderId, null, clock.instant()));
            assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isZero();
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isZero();
        transaction.executeWithoutResult(status -> {
            events.publishEvent(new com.shop.tire.order.event.OrderCreated(orderId, null, clock.instant()));
            assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isZero();
        });
        assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isEqualTo(1);
    }

    @Test
    void brokenAuditStorageDoesNotUndoHttpPurchaseOrCancellation() throws Exception {
        String product = offer(details(brand(), "Audit failure"), "100.00", 5);
        // Reject new audit entries without touching historical rows, in this isolated test database only.
        jdbc.execute("ALTER TABLE audit_log ADD CONSTRAINT test_reject_audit CHECK (false) NOT VALID");
        String orderId;
        try {
            orderId = createCustomerOrder(product, 1).get("id").asText();
            assertThat(get(customer, "/api/orders/" + orderId).get("status").asText()).isEqualTo("PENDING_PAYMENT");
            var cancelled = expect(call(customer, "POST", "/api/orders/" + orderId + "/cancellation", null), 200);
            assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("select status from customer_order where id=?", String.class, UUID.fromString(orderId)))
                    .isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("select reserved from inventory where product_id=?", Integer.class, UUID.fromString(product))).isZero();
            assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE audit_log DROP CONSTRAINT test_reject_audit");
        }
        createCustomerOrder(product, 1);
        assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isEqualTo(1);
    }

    private Map<String,Object> previewInput() { return Map.of("email","order@example.test","phone","123456789","address",deliveryAddress()); }
    private JsonNode preview(String token) throws Exception { return expect(call(token,"POST","/api/orders/preview",previewInput()),200); }
    private JsonNode createCustomerOrder(String product, int quantity) throws Exception {
        expect(call(customer,"PUT","/api/cart/items/"+product,Map.of("quantity",quantity)),200);
        return expect(call(customer,"POST","/api/orders",Map.of("quoteId",preview(customer).get("id").asText(),"checkoutKey",UUID.randomUUID())),201);
    }
    private HttpResponse<String> headerCall(String jwt, String header, String token, String method, String path, Object body) throws Exception {
        return http.send(HttpRequest.newBuilder(request(jwt,method,path,body),(name,value)->true).header(header,token).build(),HttpResponse.BodyHandlers.ofString());
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
