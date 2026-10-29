package com.shuminapp.tennis;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestDatabaseConfiguration.class)
class ProvenanceMigrationUpgradeTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void upgradesPopulatedV6AndReapplyingDoesNotChangeData() {
        // A separate schema inside the disposable test container, never the development DB.
        String schema = "upgrade_v6_test";
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .target("6").load().migrate();
            jdbcTemplate.update("""
                    INSERT INTO upgrade_v6_test.players (tour, player_id, name_first)
                    VALUES ('ATP', 100001, 'Preserved')
                    """);
            jdbcTemplate.update("""
                    INSERT INTO upgrade_v6_test.matches
                        (tour, tourney_id, tourney_date, round, match_num, winner_id, loser_id)
                    VALUES ('ATP', 'test', DATE '2026-01-05', 'F', 1, 100001, 100001)
                    """);
            jdbcTemplate.update("INSERT INTO upgrade_v6_test.source_snapshots(repository_url,commit_sha,archive_location) VALUES(?,?,?)",
                    "https://example.com/tennis","a".repeat(40),"/fixture");
            jdbcTemplate.update("""
                    INSERT INTO upgrade_v6_test.import_runs(snapshot_id,importer_commit_sha,transform_rules_sha256,quality_rules_sha256,scope_rules_sha256)
                    SELECT id,?,?,?,? FROM upgrade_v6_test.source_snapshots
                    ""","b".repeat(40),"c".repeat(64),"c".repeat(64),"c".repeat(64));
            Flyway flyway = Flyway.configure().dataSource(dataSource)
                    .schemas(schema).defaultSchema(schema).load();
            int pending=flyway.info().pending().length;
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(pending);
            flyway.validate();
            assertThat(jdbcTemplate.queryForObject("SELECT data_kind FROM upgrade_v6_test.import_runs",String.class)).isEqualTo("PLAYERS");
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT name_first FROM upgrade_v6_test.players", String.class)).isEqualTo("Preserved");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM upgrade_v6_test.matches", Long.class)).isEqualTo(1L);
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS upgrade_v6_test CASCADE");
        }
    }
}
