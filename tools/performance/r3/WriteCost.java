import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * LD-DOC-R3 write-cost measurement, at the database level (one transaction per operation, as the application commits),
 * against a throw-away copy of the synthetic 50 000-case database. THROWAWAY DATABASE ONLY, never UAT.
 *
 * <pre>
 * java -cp postgresql.jar WriteCost.java &lt;jdbc url of the copy&gt; &lt;user&gt; &lt;password&gt; with|without [cases=5000] [ops=300]
 * </pre>
 *
 * "without" disables the stored-search triggers (every trigger named search_*) and drops the search-vector, trigram and
 * party search indexes first, so the difference is the cost of keeping the search columns up to date. The operations
 * are the SQL the application sends for: a case registered with its primary involvement, proceeding and claim; a case
 * update; a document upload with its text-extraction row queued (the OCR enqueue) and then the extracted text stored;
 * a hearing update; a task update; an audit row. The bulk import registers {@code cases} such cases one transaction each.
 */
public class WriteCost {

    static final String ACTOR = "00000000-0000-4000-8000-0000000000e5";
    static Connection c;
    static final List<String> report = new ArrayList<>();

    public static void main(String[] a) throws Exception {
        c = DriverManager.getConnection(a[0], a[1], a[2]);
        c.setAutoCommit(false);
        boolean without = a[3].equals("without");
        int bulk = a.length > 4 ? Integer.parseInt(a[4]) : 5000;
        int ops = a.length > 5 ? Integer.parseInt(a[5]) : 300;
        if (without) {
            disableSearch();
        }
        UUID member = one("select id from app_user where username = 'synth.member1'");
        List<UUID> court = ids("select id from court where code like 'SYNTH-%' order by code limit 30");
        List<UUID> cases = ids("select id from legal_case where reference_no like 'SYNTH-%' order by reference_no limit " + ops);
        List<UUID> hearings = ids("select id from hearing where case_id = any(?) and status = 'SCHEDULED'", cases);
        List<UUID> tasks = ids("select id from task where case_id = any(?)", cases);
        System.out.println("variant=" + a[3] + " bulk=" + bulk + " ops=" + ops);

        measure("case registered (case+involvement+proceeding+claim)", ops, i -> register(member, court.get(i % 30), "W" + i));
        measure("case update (title)", ops, i -> update("update legal_case set title = title || ' x', updated_at = now() where id = ?", cases.get(i)));
        measure("case update (amount, no searchable column)", ops, i -> update(
                "update legal_case set claimed_amount = coalesce(claimed_amount, 0) + 1, updated_at = now() where id = ?", cases.get(i)));
        measure("document upload + OCR job queued", ops, i -> upload(cases.get(i), member));
        measure("OCR text stored (document_text update)", ops, i -> storeText(i));
        measure("hearing update (date)", Math.min(ops, hearings.size()),
                i -> update("update hearing set hearing_date = hearing_date + 1, updated_at = now() where id = ?", hearings.get(i)));
        measure("task update (title)", Math.min(ops, tasks.size()),
                i -> update("update task set title = title || ' x', updated_at = now() where id = ?", tasks.get(i)));
        measure("audit row", ops, i -> audit(i));

        long t0 = System.nanoTime();
        for (int i = 0; i < bulk; i++) {
            register(member, court.get(i % 30), "B" + i);
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        report.add(String.format("| bulk import of %d cases (one transaction each) | total %d ms | %.2f ms per case |", bulk, ms, ms / (double) bulk));
        System.out.println(String.join("\n", report));
    }

    interface Op {
        void run(int i) throws Exception;
    }

    static void measure(String name, int n, Op op) throws Exception {
        List<Long> us = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            op.run(n > 0 ? i % n : i); // warm-up (rolled into the data, not timed)
        }
        for (int i = 0; i < n; i++) {
            long t = System.nanoTime();
            op.run(i);
            us.add((System.nanoTime() - t) / 1000);
        }
        Collections.sort(us);
        double mean = us.stream().mapToLong(Long::longValue).average().orElse(0) / 1000;
        report.add(String.format("| %s | n=%d | p50 %.2f ms | p95 %.2f ms | mean %.2f ms |", name, n,
                us.get(us.size() / 2) / 1000.0, us.get((int) Math.ceil(0.95 * us.size()) - 1) / 1000.0, mean));
    }

    static void register(UUID member, UUID court, String tag) throws Exception {
        UUID id = UUID.randomUUID();
        try (PreparedStatement s = c.prepareStatement("insert into legal_case (id, reference_no, title, case_type, opened_on, status,"
                + " created_by, updated_by, primary_member_id, confidentiality) values (?, ?, ?, 'CIVIL', current_date, 'OPEN',"
                + " ?::uuid, ?::uuid, ?, 'NORMAL')")) {
            s.setObject(1, id);
            s.setString(2, "SYNTHW-" + tag + "-" + id.toString().substring(0, 6));
            s.setString(3, "Contract dispute SYNTHW " + tag);
            s.setString(4, ACTOR);
            s.setString(5, ACTOR);
            s.setObject(6, member);
            s.executeUpdate();
        }
        try (Statement s = c.createStatement()) {
            s.executeUpdate("insert into case_org_involvement (case_id, is_primary, sector_name_snapshot, department_name_snapshot,"
                    + " section_name_snapshot, created_by, updated_by) values ('" + id + "', true, 'SYNTH sector 1', 'SYNTH department 2',"
                    + " 'SYNTH section 3', '" + ACTOR + "', '" + ACTOR + "')");
            s.executeUpdate("insert into proceeding (case_id, stage, court_id, court_case_number, court_case_year, filing_date, created_by,"
                    + " updated_by) values ('" + id + "', 'FIRST_INSTANCE', '" + court + "', 'W" + id.toString().substring(0, 8) + "', 2026, current_date, '" + ACTOR
                    + "', '" + ACTOR + "')");
            s.executeUpdate("insert into claim (case_id, seq, text, kind, created_by, updated_by) values ('" + id + "', 1, 'SYNTHW claim',"
                    + " 'PRIMARY', '" + ACTOR + "', '" + ACTOR + "')");
        }
        c.commit();
    }

    static void update(String sql, UUID id) throws Exception {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, id);
            s.executeUpdate();
        }
        c.commit();
    }

    static final List<UUID> versions = new ArrayList<>();

    static void upload(UUID caseId, UUID member) throws Exception {
        UUID doc = UUID.randomUUID();
        String sha = "a".repeat(64);
        try (Statement s = c.createStatement()) {
            s.executeUpdate("insert into case_document (id, case_id, title, file_name, size_bytes, sha256, created_by) values ('" + doc
                    + "', '" + caseId + "', 'SYNTHW memo', 'memo.pdf', 2048, '" + sha + "', '" + ACTOR + "')");
            s.executeUpdate("insert into case_document_content (document_id, content) values ('" + doc + "', decode(repeat('25', 2048), 'hex'))");
            s.executeUpdate("insert into document_version (id, document_id, version_no, file_name, media_type, size_bytes, sha256,"
                    + " uploaded_by) values ('" + doc + "', '" + doc + "', 1, 'memo.pdf', 'application/pdf', 2048, '" + sha + "', '" + ACTOR + "')");
            s.executeUpdate("insert into document_version_content (version_id, content) values ('" + doc + "', decode(repeat('25', 2048), 'hex'))");
            s.executeUpdate("insert into document_text (version_id, document_id, status) values ('" + doc + "', '" + doc + "', 'PENDING')");
        }
        c.commit();
        versions.add(doc);
    }

    static void storeText(int i) throws Exception {
        UUID v = versions.get(i % versions.size());
        try (PreparedStatement s = c.prepareStatement("update document_text set status = 'EXTRACTED', method = 'OCR', text = ?,"
                + " processed_at = now(), attempts = attempts + 1 where version_id = ?")) {
            s.setString(1, "Statement of claim about a supply contract dispute, page " + i + " SYNTHW " + "lorem ipsum dolor ".repeat(60));
            s.setObject(2, v);
            s.executeUpdate();
        }
        c.commit();
    }

    static void audit(int i) throws Exception {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("insert into audit_event (actor_id, actor_username, action, target_type, target_id, outcome, request_id)"
                    + " values ('" + ACTOR + "', 'synth', 'CASE_UPDATE', 'CASE', 'x" + i + "', 'SUCCESS', 'req" + i + "')");
        }
        c.commit();
    }

    static void disableSearch() throws Exception {
        try (Statement s = c.createStatement()) {
            ResultSet r = s.executeQuery("select distinct c.relname, t.tgname from pg_trigger t join pg_class c on c.oid = t.tgrelid"
                    + " where not t.tgisinternal and t.tgname like 'search\\_%'");
            List<String> stmts = new ArrayList<>();
            while (r.next()) {
                stmts.add("alter table " + r.getString(1) + " disable trigger " + r.getString(2));
            }
            r = s.executeQuery("select indexname from pg_indexes where schemaname = current_schema() and (indexname like '%search\\_vec%'"
                    + " or indexname like 'search\\_case\\_doc\\_%' or indexname like 'search\\_link\\_doc\\_%' or indexname like 'party\\_search\\_%')"
                    + " and indexname not like '%pkey'");
            List<String> drops = new ArrayList<>();
            while (r.next()) {
                drops.add("drop index " + r.getString(1));
            }
            for (String x : stmts) {
                s.execute(x);
            }
            for (String x : drops) {
                s.execute(x);
            }
            System.out.println("disabled " + stmts.size() + " triggers, dropped " + drops.size() + " indexes");
        }
        c.commit();
    }

    static UUID one(String sql) throws Exception {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            r.next();
            return (UUID) r.getObject(1);
        }
    }

    static List<UUID> ids(String sql) throws Exception {
        return ids(sql, null);
    }

    static List<UUID> ids(String sql, List<UUID> arg) throws Exception {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            if (arg != null) {
                s.setArray(1, c.createArrayOf("uuid", arg.toArray()));
            }
            ResultSet r = s.executeQuery();
            List<UUID> out = new ArrayList<>();
            while (r.next()) {
                out.add((UUID) r.getObject(1));
            }
            return out;
        }
    }
}
