package com.shuminapp.tennis.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import com.shuminapp.tennis.TestDatabaseConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestDatabaseConfiguration.class)
class SourceRegistrationServiceTest {
    @Autowired SourceRegistrationService service;
    @Autowired JdbcTemplate jdbc;
    SourceSnapshotIdentity identity;
    @BeforeEach void setup() {
        identity = new SourceSnapshotIdentity(URI.create("https://example.com/" + UUID.randomUUID()), "a".repeat(40));
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM source_files WHERE snapshot_id IN (SELECT id FROM source_snapshots WHERE repository_url=?)", identity.repositoryUrl().toString());
        jdbc.update("DELETE FROM source_snapshots WHERE repository_url=?", identity.repositoryUrl().toString());
    }
    SourceSnapshotManifest manifest(SourceSnapshotManifest.SourceFile... files) {
        return new SourceSnapshotManifest(1, SourceSnapshotManifest.POLICY, identity, "a".repeat(40), List.of(files), List.of(), List.of());
    }
    SourceSnapshotManifest.SourceFile file(String path, SourceFileType type, String hash) {
        return new SourceSnapshotManifest.SourceFile(path,type,"a".repeat(40),hash,10,1);
    }
    @Test void completeRegistrationIsIdempotent() {
        var m = manifest(file("atp/atp_players.csv",SourceFileType.ATP_PLAYERS,"b".repeat(64)),
                         file("wta/wta_players.csv",SourceFileType.WTA_PLAYERS,"c".repeat(64)));
        var first = service.registerVerified(m, Path.of("/archive"));
        assertThat(service.registerVerified(m,Path.of("/archive"))).isEqualTo(first);
        assertThat(first.fileIds()).hasSize(2);
    }
    @Test void laterInvalidFileRollsBackSnapshotAndEarlierFile() {
        var m = manifest(file("atp/atp_players.csv",SourceFileType.ATP_PLAYERS,"b".repeat(64)),
                         file("wta/wta_players.csv",SourceFileType.WTA_PLAYERS,"invalid"));
        assertThrows(IllegalArgumentException.class, () -> service.registerVerified(m,Path.of("/archive")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM source_snapshots WHERE repository_url=?", Long.class, identity.repositoryUrl().toString())).isZero();
    }
    @Test void conflictRollsBackEarlierNewFileAndPreservesExisting() {
        var original = file("wta/wta_players.csv",SourceFileType.WTA_PLAYERS,"c".repeat(64));
        var registered = service.registerVerified(manifest(original),Path.of("/archive"));
        var changed = manifest(file("atp/atp_players.csv",SourceFileType.ATP_PLAYERS,"b".repeat(64)),
                file("wta/wta_players.csv",SourceFileType.WTA_PLAYERS,"d".repeat(64)));
        assertThrows(IllegalStateException.class, () -> service.registerVerified(changed,Path.of("/archive")));
        assertThat(service.registerVerified(manifest(original),Path.of("/archive"))).isEqualTo(registered);
    }
    @Test void rejectsOmittedPreviouslyRegisteredFile() {
        var a=file("atp/atp_players.csv",SourceFileType.ATP_PLAYERS,"b".repeat(64));
        var w=file("wta/wta_players.csv",SourceFileType.WTA_PLAYERS,"c".repeat(64));
        service.registerVerified(manifest(a,w),Path.of("/archive"));
        assertThrows(IllegalStateException.class, () -> service.registerVerified(manifest(a),Path.of("/archive")));
    }
    @Test void concurrentRegistrationsConvergeOnSameIds() throws Exception {
        var m=manifest(file("atp/atp_players.csv",SourceFileType.ATP_PLAYERS,"b".repeat(64)));
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<SourceRegistrationService.Registration> task=()->{
                start.await(); return service.registerVerified(m,Path.of("/archive"));
            };
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();
            assertThat(a.get(15,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(b.get(15,java.util.concurrent.TimeUnit.SECONDS));
        }
    }
    @Test void missingArchiveDoesNotRegister() {
        assertThrows(java.io.IOException.class, () -> service.register(Path.of("/missing"), identity,Path.of("/missing/archive")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM source_snapshots WHERE repository_url=?",Long.class,identity.repositoryUrl().toString())).isZero();
    }
}
