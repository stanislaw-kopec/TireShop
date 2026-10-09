package com.shop.tire;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApplicationTests {

    @LocalServerPort
    private int port;

    @Test
    void healthReportsWorkingApplicationAndDatabase() throws Exception {
        var response = get("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void helloEndpointReturnsGreeting() throws Exception {
        var response = get("/api/hello");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("Hello World!");
    }

    @Test
    void apiDocumentationIsAvailable() throws Exception {
        assertThat(get("/v3/api-docs").statusCode()).isEqualTo(200);
        assertThat(get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> get(String path) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
