package com.shuminapp.tennis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DatabaseConnectionTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void connectsToExpectedDatabase() {
        String database = jdbcTemplate.queryForObject(
            "SELECT current_database()",
            String.class
        );

        Integer result = jdbcTemplate.queryForObject(
            "SELECT 1",
            Integer.class
        );

        assertThat(database).isEqualTo("tennis");
        assertThat(result).isEqualTo(1);
    }

}
