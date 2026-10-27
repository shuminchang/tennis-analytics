package com.shuminapp.tennis.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import com.shuminapp.tennis.TestDatabaseConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Import(TestDatabaseConfiguration.class)
class PlayerImportServiceTest {
    @Autowired PlayerImportService service;
    @Autowired JdbcTemplate jdbc;
    @TempDir Path temp;
    GitFixture fixture;
    SourceSnapshotIdentity identity;
    Path archive;
    void prepare(String atp,String wta) throws Exception {
        fixture=new GitFixture(temp.resolve("repo"));
        fixture.write("atp/atp_players.csv",atp); fixture.write("wta/wta_players.csv",wta);
        identity=fixture.commit();archive=temp.resolve("archive");
        new SourceSnapshotStore().prepare(fixture.root,identity,archive);
    }
    @AfterEach void cleanup() throws Exception {
        // Dedicated context's candidate DB contains only synthetic import data; remove children first.
        jdbc.execute("TRUNCATE quality_issue_impacts,quality_issues,player_sources,players,source_files,source_snapshots,import_runs CASCADE");
        try(var paths=Files.walk(temp)) {
            for(Path p:paths.sorted().toList()) if(Files.isDirectory(p)) Files.setPosixFilePermissions(p,PosixFilePermissions.fromString("rwx------"));
        }
    }
    @Test void importsBothToursRetainsIssuesAndReusesSuccessfulRun() throws Exception {
        String header=GitFixture.PLAYERS.substring(0,GitFixture.PLAYERS.indexOf('\n')+1);
        prepare(header+"1,Roger,Federer,R,19810808,SUI,185,Q1\n2,U,Unknown,U,19000000,?,3,\ninvalid,X,X,,,,,\n",
                header+"1,Serena,Williams,R,19810926,USA,175,Q2\n");
        var result=service.importPlayers(fixture.root,identity,archive,"a".repeat(40));
        assertThat(result.imported()).isEqualTo(3);assertThat(result.quarantined()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quality_issues",Long.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT dob FROM players WHERE tour='ATP' AND player_id=2",java.sql.Date.class)).isNull();
        assertThat(service.importPlayers(fixture.root,identity,archive,"a".repeat(40)).reused()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM import_runs",Long.class)).isEqualTo(1);
        assertThrows(IllegalStateException.class,()->service.importPlayers(fixture.root,identity,archive,"b".repeat(40)));
    }
    @Test void duplicateIdentityRollsBackPlayersAndKeepsFailedRun() throws Exception {
        prepare(GitFixture.PLAYERS+"100001,Other,Name,R,,USA,,\n",GitFixture.PLAYERS);
        assertThrows(IllegalStateException.class,()->service.importPlayers(fixture.root,identity,archive,"a".repeat(40)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM players",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM player_sources",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM import_runs",String.class)).isEqualTo("FAILED");
        assertThrows(IllegalStateException.class,()->service.importPlayers(fixture.root,identity,archive,"a".repeat(40)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM import_runs WHERE status='FAILED'",Long.class)).isEqualTo(2);
    }
    @Test void rejectsTamperingBeforeDatabaseRegistration() throws Exception {
        prepare(GitFixture.PLAYERS,GitFixture.PLAYERS);
        Path file=archive.resolve("atp/atp_players.csv"); Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"));
        Files.writeString(file,"tampered");
        assertThrows(java.io.IOException.class,()->service.importPlayers(fixture.root,identity,archive,"a".repeat(40)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM import_runs",Long.class)).isZero();
    }
}
