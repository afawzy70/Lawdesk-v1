import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LD-040 expensive-query harness. Runs against a throwaway, local PostgreSQL only (never UAT, never a shared database):
 * applies Flyway migration directories and seed scripts in order, then captures EXPLAIN (ANALYZE, BUFFERS) of every
 * query in a plans file. No dependency beyond the PostgreSQL JDBC driver the backend already uses.
 *
 * <pre>
 * java -cp ~/.m2/repository/org/postgresql/postgresql/42.7.14/postgresql-42.7.14.jar tools/performance/db/QueryPlans.java \
 *     jdbc:postgresql://127.0.0.1:55432/postgres?user=lawdesk \
 *     apply:backend/src/main/resources/db/migration apply:tools/performance/db/seed-develop.sql \
 *     plans:tools/performance/db/plans-develop.sql
 * </pre>
 *
 * Plans file format: blocks start with {@code -- @query <name>} (EXPLAIN it) or {@code -- @rows <name>} (print its
 * result, for row counts); {@code -- @set NAME = <sql>} evaluates a one-value
 * query and substitutes {@code :NAME} in later blocks as a quoted literal (used for the uuid[] arrays the reports
 * send). Each query runs once to warm the cache, then once under EXPLAIN.
 */
public class QueryPlans {

    private static final Pattern VERSION = Pattern.compile("V(\\d+)__.*\\.sql");
    private static final Pattern SET = Pattern.compile("--\\s*@set\\s+(\\w+)\\s*=\\s*(.+)");
    private static final Pattern QUERY = Pattern.compile("--\\s*@(query|rows)\\s+(.+)");

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || !args[0].startsWith("jdbc:postgresql://127.0.0.1:")) {
            System.err.println("usage: QueryPlans jdbc:postgresql://127.0.0.1:<port>/<db>?user=<u> apply:<dir|file>... "
                    + "plans:<file>...   (local instances only)");
            System.exit(2);
        }
        String url = args[0] + (args[0].contains("?") ? "&" : "?") + "preferQueryMode=simple";
        try (Connection db = DriverManager.getConnection(url)) {
            db.setAutoCommit(true);
            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
                if (arg.startsWith("apply:")) {
                    for (Path file : sqlFiles(Path.of(arg.substring(6)))) {
                        long start = System.nanoTime();
                        try (Statement st = db.createStatement()) {
                            st.execute(Files.readString(file));
                        }
                        System.out.printf("-- applied %s in %d ms%n", file, (System.nanoTime() - start) / 1_000_000);
                    }
                } else if (arg.startsWith("plans:")) {
                    plans(db, Path.of(arg.substring(6)), values);
                } else {
                    throw new IllegalArgumentException("unknown argument " + arg);
                }
            }
        }
    }

    private static List<Path> sqlFiles(Path path) throws Exception {
        if (!Files.isDirectory(path)) {
            return List.of(path);
        }
        try (var files = Files.list(path)) {
            return files.filter(f -> VERSION.matcher(f.getFileName().toString()).matches())
                    .sorted(Comparator.comparingLong(f -> {
                        Matcher m = VERSION.matcher(f.getFileName().toString());
                        m.matches();
                        return Long.parseLong(m.group(1));
                    })).toList();
        }
    }

    private static void plans(Connection db, Path file, Map<String, String> values) throws Exception {
        String name = null;
        StringBuilder sql = new StringBuilder();
        List<String[]> blocks = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            Matcher set = SET.matcher(line.strip());
            Matcher query = QUERY.matcher(line.strip());
            if (set.matches() || query.matches()) {
                if (name != null && !sql.toString().isBlank()) {
                    blocks.add(new String[] {name, sql.toString()});
                }
                sql.setLength(0);
                if (set.matches()) {
                    blocks.add(new String[] {"@set " + set.group(1), set.group(2)});
                    name = null;
                } else {
                    name = (query.group(1).equals("rows") ? "@rows " : "") + query.group(2).strip();
                }
            } else if (name != null && !line.strip().startsWith("--")) {
                sql.append(line).append('\n');
            }
        }
        if (name != null && !sql.toString().isBlank()) {
            blocks.add(new String[] {name, sql.toString()});
        }
        for (String[] block : blocks) {
            String text = substitute(block[1].strip().replaceAll(";\\s*$", ""), values);
            if (block[0].startsWith("@set ")) {
                try (Statement st = db.createStatement(); ResultSet rs = st.executeQuery(text)) {
                    rs.next();
                    values.put(block[0].substring(5), rs.getString(1));
                }
                continue;
            }
            if (block[0].startsWith("@rows ")) {
                System.out.println("== " + block[0].substring(6));
                try (Statement st = db.createStatement(); ResultSet rs = st.executeQuery(text)) {
                    int columns = rs.getMetaData().getColumnCount();
                    while (rs.next()) {
                        StringBuilder row = new StringBuilder();
                        for (int c = 1; c <= columns; c++) {
                            row.append(c > 1 ? " | " : "").append(rs.getMetaData().getColumnLabel(c)).append('=')
                                    .append(rs.getString(c));
                        }
                        System.out.println(row);
                    }
                }
                System.out.println();
                continue;
            }
            try (Statement st = db.createStatement()) {
                st.execute(text); // warm-up
            }
            System.out.println("== " + block[0]);
            System.out.println(summarise(block[1].strip()));
            try (Statement st = db.createStatement();
                    ResultSet rs = st.executeQuery("explain (analyze, buffers, timing) " + text)) {
                while (rs.next()) {
                    String line = rs.getString(1);
                    // Literal uuid[] arrays of thousands of ids would swamp the evidence.
                    System.out.println(line.length() > 600 ? line.substring(0, 600) + " ...(truncated)" : line);
                }
            }
            System.out.println();
        }
    }

    private static String substitute(String text, Map<String, String> values) {
        for (var entry : values.entrySet()) {
            text = text.replace(":" + entry.getKey(), "'" + entry.getValue().replace("'", "''") + "'");
        }
        return text;
    }

    /** The query as written in the plans file, shortened to keep the evidence readable. */
    private static String summarise(String sql) {
        String flat = sql.replaceAll("\\s+", " ");
        return "-- " + (flat.length() > 400 ? flat.substring(0, 400) + " ..." : flat);
    }
}
