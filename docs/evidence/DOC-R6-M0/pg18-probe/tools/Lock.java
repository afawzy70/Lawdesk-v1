import java.sql.*; import java.util.*; import java.util.concurrent.*;
/** Lock-order and concurrency probes against the disposable PostgreSQL 18 database. Usage: Lock <scenario> */
public class Lock {
  static final String URL = "jdbc:postgresql://127.0.0.1:54329/lawdesk?options=-csearch_path%3Dlawdesk";
  static final String P  = "'00000000-0000-0000-0000-0000000000b1'", C4 = "'00000000-0000-0000-0000-0000000000b4'", C5 = "'00000000-0000-0000-0000-0000000000b5'";
  static final long T0 = System.currentTimeMillis();
  static synchronized void log(String who, String what) { System.out.printf("  +%4dms %-6s %s%n", System.currentTimeMillis()-T0, who, what); }
  static Connection open(String user) throws Exception { Connection c = DriverManager.getConnection(URL, user, ""); c.setAutoCommit(false); return c; }
  static void exec(Connection c, String who, String sql) throws SQLException {
    try (Statement s = c.createStatement()) { s.execute(sql); log(who, "ok    " + sql.replaceAll("\\s+", " ").substring(0, Math.min(92, sql.replaceAll("\\s+"," ").length()))); }
  }
  static String run(String who, Connection c, Callable<String> body) {
    try { String r = body.call(); c.commit(); log(who, "COMMIT " + r); return "commit"; }
    catch (SQLException e) { try { c.rollback(); } catch (Exception ig) {} log(who, "ROLLBACK [" + e.getSQLState() + "] " + e.getMessage().lines().findFirst().orElse("")); return e.getSQLState(); }
    catch (Exception e) { log(who, "ERROR " + e); return "error"; }
  }
  public static void main(String[] a) throws Exception {
    switch (a[0]) {
      case "inverted" -> inverted(); case "unified" -> unified(); case "phantom" -> phantom();
      case "referral" -> referral(); case "closure" -> closure(); default -> throw new IllegalArgumentException(a[0]);
    }
  }
  // S1: the order that 05 C.5 proposed (parent first) against ProceedingService.update (child first).
  static void inverted() throws Exception {
    Connection x = open("lawdesk_owner"), y = open("lawdesk_owner"); ExecutorService ex = Executors.newFixedThreadPool(2);
    Future<String> f1 = ex.submit(() -> run("update", x, () -> { exec(x, "update", "select 1 from proceeding where id = "+C4+" for update"); Thread.sleep(400); exec(x, "update", "select 1 from proceeding where id = "+P+" for update"); return "child then parent"; }));
    Thread.sleep(100);
    Future<String> f2 = ex.submit(() -> run("final", y, () -> { exec(y, "final", "select 1 from proceeding where id = "+P+" for update"); Thread.sleep(400); exec(y, "final", "select 1 from proceeding where id = "+C4+" for update"); return "parent then child"; }));
    System.out.println("RESULT inverted: " + f1.get() + " / " + f2.get()); ex.shutdown();
  }
  // S2: the single global discipline: children (ascending id) first, then the parent. Both transactions use it.
  static void unified() throws Exception {
    Connection x = open("lawdesk_owner"), y = open("lawdesk_owner"); ExecutorService ex = Executors.newFixedThreadPool(2);
    Callable<String> body1 = () -> { exec(x, "update", "select id from proceeding where parent_proceeding_id = "+P+" order by id for update"); Thread.sleep(400); exec(x, "update", "select 1 from proceeding where id = "+P+" for update"); return "children then parent"; };
    Callable<String> body2 = () -> { exec(y, "final", "select id from proceeding where parent_proceeding_id = "+P+" order by id for update"); Thread.sleep(400); exec(y, "final", "select 1 from proceeding where id = "+P+" for update"); return "children then parent"; };
    Future<String> f1 = ex.submit(() -> run("update", x, body1)); Thread.sleep(100); Future<String> f2 = ex.submit(() -> run("final", y, body2));
    System.out.println("RESULT unified: " + f1.get() + " / " + f2.get()); ex.shutdown();
  }
  // S3: finality locks the EXISTING children, then the parent, then RE-QUERIES the children; a filing that created a child meanwhile is seen.
  static void phantom() throws Exception {
    Connection fin = open("lawdesk_owner"), fil = open("lawdesk_owner"); ExecutorService ex = Executors.newFixedThreadPool(2);
    Future<String> f1 = ex.submit(() -> run("final", fin, () -> {
      exec(fin, "final", "select id from proceeding where parent_proceeding_id = "+P+" order by id for update");
      Thread.sleep(600);
      exec(fin, "final", "select 1 from proceeding where id = "+P+" for update");   // waits for the filing
      try (Statement s = fin.createStatement(); ResultSet r = s.executeQuery("select count(*) from proceeding where parent_proceeding_id = "+P)) { r.next(); int n = r.getInt(1);
        log("final", "children visible AFTER the parent lock: " + n); if (n > 2) throw new SQLException("a child proceeding appeared: finality refused (APPEAL_FILED)", "LD040"); }
      return "finality confirmed"; }));
    Thread.sleep(150);
    Future<String> f2 = ex.submit(() -> run("filing", fil, () -> {
      exec(fil, "filing", "select 1 from proceeding where id = "+P+" for update");   // R6 filing takes the parent first (it creates the child)
      exec(fil, "filing", "insert into proceeding (id, case_id, stage, court_id, court_case_number, court_case_year, filing_date, parent_proceeding_id, created_by, updated_by) values ('00000000-0000-0000-0000-0000000000b9','00000000-0000-0000-0000-0000000000c1','APPEAL','00000000-0000-0000-0000-0000000000d2','999',2026,date '2026-07-09',"+P+",'00000000-0000-0000-0000-0000000000a1','00000000-0000-0000-0000-0000000000a1')");
      return "child b9 created"; }));
    System.out.println("RESULT phantom: " + f1.get() + " / " + f2.get()); ex.shutdown();
  }
  // S4: two concurrent completions of the SAME complaint, each with its own RESTRICTED case.
  static void referral() throws Exception {
    Connection x = open("lawdesk_legal"), y = open("lawdesk_legal"); ExecutorService ex = Executors.newFixedThreadPool(2);
    String call = "select r6_complete_referral('00000000-0000-0000-0000-00000000f303', '%s', '00000000-0000-0000-0000-0000000000a2')";
    String ins = "insert into legal_case (id, reference_no, title, case_type, opened_on, confidentiality, created_by, updated_by) values ('%s', '%s', 'concurrent referral', 'CIVIL', current_date, 'RESTRICTED', '00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-0000000000a1')";
    Future<String> f1 = ex.submit(() -> run("refA", x, () -> { exec(x, "refA", ins.formatted("00000000-0000-0000-0000-00000000c3a1","LD-2026-90351")); exec(x, "refA", call.formatted("00000000-0000-0000-0000-00000000c3a1")); Thread.sleep(500); return "referral completed"; }));
    Thread.sleep(150);
    Future<String> f2 = ex.submit(() -> run("refB", y, () -> { exec(y, "refB", ins.formatted("00000000-0000-0000-0000-00000000c3b1","LD-2026-90352")); exec(y, "refB", call.formatted("00000000-0000-0000-0000-00000000c3b1")); return "referral completed"; }));
    System.out.println("RESULT referral: " + f1.get() + " / " + f2.get());
    try (Connection v = open("lawdesk_owner"); Statement s = v.createStatement(); ResultSet r = s.executeQuery("select (select count(*) from complaint_case_link where complaint_id = '00000000-0000-0000-0000-00000000f303'), (select count(*) from legal_case where id in ('00000000-0000-0000-0000-00000000c3a1','00000000-0000-0000-0000-00000000c3b1'))")) { r.next(); System.out.println("RESULT referral state: links_for_complaint=" + r.getInt(1) + " cases_surviving=" + r.getInt(2)); }
    ex.shutdown();
  }
  // S5: case closure (an UPDATE of legal_case) against an R6 write that holds legal_case FOR SHARE, in both orders.
  static void closure() throws Exception {
    String C = "'00000000-0000-0000-0000-0000000000c1'";
    for (int round = 1; round <= 2; round++) {
      System.out.println("-- round " + round + (round == 1 ? ": the R6 write first, closure second" : ": closure first, the R6 write second"));
      Connection w = open("lawdesk_owner"), cl = open("lawdesk_owner"); ExecutorService ex = Executors.newFixedThreadPool(2); final int rd = round;
      Callable<String> r6 = () -> { exec(w, "r6", "select 1 from legal_case where id = "+C+" for share"); Thread.sleep(rd == 1 ? 600 : 0);
        try (Statement s = w.createStatement(); ResultSet r = s.executeQuery("select status from legal_case where id = "+C)) { r.next(); String st = r.getString(1); log("r6", "case status seen under the share lock: " + st);
          if (st.equals("CLOSED")) throw new SQLException("case is closed: R6 write refused (CASE_READ_ONLY)", "LD041"); } return "party deadline written"; };
      Callable<String> close = () -> { exec(cl, "close", "update legal_case set status = 'CLOSED' where id = "+C); Thread.sleep(300); return "case closed"; };
      Future<String> f1, f2;
      if (round == 1) { f1 = ex.submit(() -> run("r6", w, r6)); Thread.sleep(150); f2 = ex.submit(() -> run("close", cl, close)); }
      else { f2 = ex.submit(() -> run("close", cl, close)); Thread.sleep(150); f1 = ex.submit(() -> run("r6", w, r6)); }
      System.out.println("RESULT closure round " + round + ": r6=" + f1.get() + " close=" + f2.get()); ex.shutdown();
      try (Connection v = open("lawdesk_owner"); Statement s = v.createStatement()) { s.execute("update legal_case set status = 'OPEN' where id = "+C); v.commit(); }
    }
  }
}
