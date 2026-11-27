import java.sql.*; import java.nio.file.*; import java.util.*; import java.util.regex.*;
public class Migrate {
  public static void main(String[] a) throws Exception {
    List<Path> files = new ArrayList<>();
    try (var s = Files.list(Path.of(a[2]))) { s.filter(p->p.getFileName().toString().matches("V\\d+__.*\\.sql")).forEach(files::add); }
    files.sort(Comparator.comparingInt(p -> Integer.parseInt(p.getFileName().toString().replaceAll("^V(\\d+)__.*","$1"))));
    try (Connection c = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:54329/"+a[1]+"?options=-csearch_path%3Dlawdesk", a[0], "")) {
      c.setAutoCommit(false); int n=0;
      for (Path f : files) { try (Statement st=c.createStatement()) { st.execute(Files.readString(f)); c.commit(); n++; }
        catch (SQLException e) { c.rollback(); System.out.println("FAIL "+f.getFileName()+" "+e.getMessage().lines().findFirst().orElse("")); return; } }
      System.out.println("applied "+n+" migrations as "+a[0]);
    }
  }
}
