package com.shuminapp.tennis.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import com.shuminapp.tennis.TestDatabaseConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabaseConfiguration.class)
class HealthControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointReturnsUp() {
        var response = restTemplate.getForEntity(
                "/v1/health",
                HealthController.HealthResponse.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType())
                .isNotNull()
                .satisfies(contentType ->
                        assertThat(contentType.isCompatibleWith(MediaType.APPLICATION_JSON))
                                .isTrue());
        assertThat(response.getBody())
                .isEqualTo(new HealthController.HealthResponse("UP"));
    }
}