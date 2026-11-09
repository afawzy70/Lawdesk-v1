import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.*;

/** Starts a throwaway embedded PostgreSQL (real binaries, no Docker), prints its port into the given file, and waits. */
public class EmbeddedPg {
    public static void main(String[] args) throws Exception {
        EmbeddedPostgres pg = EmbeddedPostgres.builder().start();
        try (var c = pg.getPostgresDatabase().getConnection(); var s = c.createStatement()) {
            s.execute("CREATE DATABASE lawdesk_ux");
        }
        Files.writeString(Path.of(args[0]), String.valueOf(pg.getPort()));
        Thread.currentThread().join();
    }
}
