package com.shop.tire;

import java.net.URI;
import java.net.http.*;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.*;
import static org.assertj.core.api.Assertions.*;

/** Real HTTP + PostgreSQL + SMTP, including restart against the same isolated database. */
@Testcontainers
class MvpJourneyIntegrationTests {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17");
    @Container static final GenericContainer<?> MAIL=new GenericContainer<>("axllent/mailpit:v1.31.4").withExposedPorts(1025,8025);
    private final String jwtSecret=secret();
    private final String trackingSecret=secret();
    private final String password="Mvp-"+secret();
    private final String adminEmail="admin@example.test";
    private ObjectMapper json;
    private HttpClient http;
    private int port;

    @Test
    void completePurchasesRealMailAndRestartPreserveMvpBehavior() throws Exception {
        try (var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            http=client;
            String product;
            String customerId;
            String customerOrder;
            String guestOrder;
            String purchaseToken;
            String originalTracking;
            String expiredOrder;
            try (var app=start(false)) {
                var jdbc=app.getBean(JdbcTemplate.class);
                assertMigrations(jdbc);
                assertThat(call(null,"GET","/actuator/health",null,200).path("status").asText()).isEqualTo("UP");
                String admin=login(adminEmail,password);
                call(admin,"POST","/api/admin/employees",
                        Map.of("email","employee@example.test","firstName","Test","lastName","Employee"),201);
                String invitation=mailToken("employee@example.test");
                call(null,"POST","/api/auth/password-actions/complete",Map.of("token",invitation,"password",password),204);
                String employee=login("employee@example.test",password);
                product=call(employee,"POST","/api/employee/tires",Map.of("manufacturer","MVP Brand","model","Winter",
                        "widthMm",205,"aspectRatio",55,"rimDiameterIn",16,"season","WINTER"),201).get("id").asText();
                call(employee,"POST","/api/employee/inventory/"+product+"/movements",
                        Map.of("operationId",UUID.randomUUID(),"type","RECEIPT","quantityDelta",12,"reason","MVP delivery"),200);
                assertThat(call(null,"GET","/api/tires",null,200).get("totalElements").asInt()).isZero();
                call(admin,"PUT","/api/admin/tires/"+product+"/price",Map.of("priceGross",new java.math.BigDecimal("100.50")),200);
                call(admin,"PUT","/api/admin/tires/"+product+"/sale",Map.of("onSale",true),200);
                assertThat(call(null,"GET","/api/tires?widthMm=205&season=WINTER&sort=PRICE_ASC",null,200).get("totalElements").asInt()).isEqualTo(1);
                customerId=call(null,"POST","/api/auth/register",Map.of("email","customer@example.test","password",password,
                        "firstName","Test","lastName","Customer"),201).get("id").asText();
                String customer=login("customer@example.test",password);
                call(customer,"PUT","/api/account/me/address",address(),200);
                String cart=call(null,"POST","/api/guest/cart",null,201).get("token").asText();
                header(null,"X-Cart-Token",cart,"PUT","/api/guest/cart/items/"+product,Map.of("quantity",1),200);
                call(customer,"PUT","/api/cart/items/"+product,Map.of("quantity",3),200);
                var merged=header(customer,"X-Cart-Token",cart,"POST","/api/cart/merge",null,200);
                assertThat(merged.get("totalQuantity").asInt()).isEqualTo(4);
                assertThat(merged.get("shippingGross").decimalValue()).isEqualByComparingTo("0");
                var quote=call(customer,"POST","/api/orders/preview",Map.of("phone","123456789"),200);
                customerOrder=call(customer,"POST","/api/orders",Map.of("quoteId",quote.get("id").asText(),"checkoutKey",UUID.randomUUID()),201).get("id").asText();
                pay(customer,null,customerOrder);
                fulfill(employee,customerOrder,"CUSTOMER-123");
                assertThat(call(customer,"GET","/api/orders/"+customerOrder,null,200).get("status").asText()).isEqualTo("DELIVERED");
                assertThat(call(customer,"GET","/api/orders",null,200).at("/items/0/trackingNumber").asText()).isEqualTo("CUSTOMER-123");

                cart=call(null,"POST","/api/guest/cart",null,201).get("token").asText();
                var guestCart=header(null,"X-Cart-Token",cart,"PUT","/api/guest/cart/items/"+product,Map.of("quantity",3),200);
                assertThat(guestCart.get("shippingGross").decimalValue()).isEqualByComparingTo("20");
                quote=header(null,"X-Cart-Token",cart,"POST","/api/guest/orders/preview",
                        Map.of("email","guest@example.test","phone","123456789","address",address()),200);
                purchaseToken=quote.get("purchaseToken").asText();
                guestOrder=header(null,"X-Purchase-Token",purchaseToken,"POST","/api/guest/orders",
                        Map.of("quoteId",quote.get("id").asText(),"checkoutKey",UUID.randomUUID()),201).get("id").asText();
                pay(null,purchaseToken,guestOrder);
                originalTracking=header(null,"X-Purchase-Token",purchaseToken,"GET","/api/guest/orders/"+guestOrder+"/tracking-link",null,200).get("trackingUrl").asText();
                assertThat(jdbc.queryForObject("select count(*) from order_email_outbox where status='PENDING'",Integer.class)).isEqualTo(1);

                // Leave an expired reservation and queued mail for real jobs to recover after restart.
                call(customer,"PUT","/api/cart/items/"+product,Map.of("quantity",1),200);
                quote=call(customer,"POST","/api/orders/preview",Map.of("phone","123456789"),200);
                expiredOrder=call(customer,"POST","/api/orders",Map.of("quoteId",quote.get("id").asText(),"checkoutKey",UUID.randomUUID()),201).get("id").asText();
                Instant end=app.getBean(Clock.class).instant().minusSeconds(1);
                jdbc.update("update customer_order set created_at=?,reservation_expires_at=? where id=?",
                        Timestamp.from(end.minusSeconds(900)),Timestamp.from(end),UUID.fromString(expiredOrder));
            }
            try (var app=start(true)) {
                var jdbc=app.getBean(JdbcTemplate.class);
                assertMigrations(jdbc);
                String admin=login(adminEmail,password);
                String employee=login("employee@example.test",password);
                String customer=login("customer@example.test",password);
                assertThat(call(customer,"GET","/api/orders/"+customerOrder,null,200).get("status").asText()).isEqualTo("DELIVERED");
                assertThat(header(null,"X-Purchase-Token",purchaseToken,"GET","/api/guest/orders/"+guestOrder+"/tracking-link",null,200)
                        .get("trackingUrl").asText().equals(originalTracking)).isTrue();
                String mailText=waitForMail("guest@example.test");
                assertThat(mailText.contains(originalTracking)).isTrue();
                String tracking=extractToken(mailText);
                fulfill(employee,guestOrder,"GUEST-456");
                var status=header(null,"X-Tracking-Token",tracking,"GET","/api/order-tracking",null,200);
                assertThat(status.get("status").asText()).isEqualTo("DELIVERED");
                assertThat(status.get("trackingNumber").asText()).isEqualTo("GUEST-456");
                assertThat(status.size()).isEqualTo(3);
                long until=System.nanoTime()+Duration.ofSeconds(20).toNanos();
                while (call(employee,"GET","/api/employee/inventory/"+product,null,200).get("reserved").asInt()!=0 && System.nanoTime()<until)
                    Thread.sleep(100);
                var stock=call(employee,"GET","/api/employee/inventory/"+product,null,200);
                assertThat(stock.get("onHand").asInt()).isEqualTo(5);
                assertThat(stock.get("reserved").asInt()).isZero();
                assertThat(jdbc.queryForObject("select status from customer_order where id=?",String.class,UUID.fromString(expiredOrder))).isEqualTo("CANCELLED");
                assertThat(jdbc.queryForObject("select status from order_email_outbox",String.class)).isEqualTo("SENT");
                call(admin,"POST","/api/admin/accounts/"+customerId+"/password-reset",null,204);
                String reset=mailToken("customer@example.test");
                String newPassword="Changed-"+secret();
                call(null,"POST","/api/auth/password-actions/complete",Map.of("token",reset,"password",newPassword),204);
                call(customer,"GET","/api/account/me",null,401);
                String renewed=login("customer@example.test",newPassword);
                assertThat(call(renewed,"GET","/api/orders/"+customerOrder,null,200).get("status").asText()).isEqualTo("DELIVERED");
            }
        }
    }

    private ConfigurableApplicationContext start(boolean jobs) {
        var app=new SpringApplicationBuilder(Application.class).registerShutdownHook(false).run(
                "--spring.profiles.active=external","--server.port=0","--spring.docker.compose.enabled=false",
                "--spring.datasource.url="+POSTGRES.getJdbcUrl(),"--spring.datasource.username="+POSTGRES.getUsername(),
                "--spring.datasource.password="+POSTGRES.getPassword(),"--app.jwt.secret="+jwtSecret,
                "--app.tracking.secret="+trackingSecret,"--app.bootstrap.enabled=true",
                "--app.bootstrap.admin-email="+adminEmail,"--app.bootstrap.admin-password="+password,
                "--spring.mail.host="+MAIL.getHost(),"--spring.mail.port="+MAIL.getMappedPort(1025),
                "--spring.mail.username=","--spring.mail.password=","--spring.mail.properties.mail.smtp.auth=false",
                "--spring.mail.properties.mail.smtp.starttls.enable=false",
                "--app.tracking.mail-job-enabled="+jobs,"--app.order.expiry-job-enabled="+jobs,
                "--app.order.expiry-delay-ms=100","--spring.main.banner-mode=off");
        port=app.getEnvironment().getRequiredProperty("local.server.port",Integer.class);
        json=app.getBean(ObjectMapper.class);
        return app;
    }
    private void assertMigrations(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success",Integer.class)).isEqualTo(11);
    }
    private void pay(String jwt, String purchase, String order) throws Exception {
        String path=(purchase==null?"/api/orders/":"/api/guest/orders/")+order+"/payments";
        var payment=header(jwt,"X-Purchase-Token",purchase,"POST",path,Map.of("requestKey",UUID.randomUUID()),201);
        header(jwt,"X-Purchase-Token",purchase,"POST",path+"/"+payment.get("id").asText()+"/simulation-result",Map.of("outcome","SUCCEEDED"),200);
    }
    private void fulfill(String employee, String order, String shipment) throws Exception {
        for (String status:List.of("PROCESSING","SHIPPED","DELIVERED")) {
            var input=new HashMap<String,Object>(Map.of("operationId",UUID.randomUUID(),"status",status));
            if (status.equals("SHIPPED")) input.put("trackingNumber",shipment);
            call(employee,"PATCH","/api/employee/orders/"+order+"/status",input,200);
        }
    }
    private String login(String email, String password) throws Exception {
        return call(null,"POST","/api/auth/login",Map.of("email",email,"password",password),200).get("accessToken").asText();
    }
    private JsonNode call(String jwt, String method, String path, Object body, int expected) throws Exception {
        return header(jwt,null,null,method,path,body,expected);
    }
    private JsonNode header(String jwt, String header, String token, String method, String path, Object body, int expected) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(20)).header("Content-Type","application/json");
        if (jwt!=null) builder.header("Authorization","Bearer "+jwt);
        if (token!=null) builder.header(header,token);
        var response=http.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        // Deliberately omit bodies: successful responses can contain credentials.
        assertThat(response.statusCode()).as("%s %s",method,path).isEqualTo(expected);
        return response.body().isEmpty()?json.nullNode():json.readTree(response.body());
    }
    private String mailToken(String email) throws Exception { return extractToken(waitForMail(email)); }
    private String extractToken(String text) {
        var matcher=Pattern.compile("token=([A-Za-z0-9_-]{43})").matcher(text);
        assertThat(matcher.find()).as("Message contains action/tracking token").isTrue();
        return matcher.group(1);
    }
    private String waitForMail(String email) throws Exception {
        long until=System.nanoTime()+Duration.ofSeconds(30).toNanos();
        while (System.nanoTime()<until) {
            var listing=mailGet("/api/v1/messages");
            for (var message:listing.path("messages")) {
                for (var to:message.path("To")) {
                    if (email.equalsIgnoreCase(to.path("Address").asText()))
                        return mailGet("/api/v1/message/"+message.get("ID").asText()).get("Text").asText();
                }
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Expected message did not arrive in isolated Mailpit.");
    }
    private JsonNode mailGet(String path) throws Exception {
        var response=http.send(HttpRequest.newBuilder(URI.create("http://"+MAIL.getHost()+":"+MAIL.getMappedPort(8025)+path))
                .timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }
    private Map<String,Object> address() {
        return Map.of("recipientName","MVP Test","street","Testowa","buildingNumber","1","postalCode","00-001","city","Warszawa","countryCode","PL");
    }
    private static String secret() { return Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(32)); }
}
