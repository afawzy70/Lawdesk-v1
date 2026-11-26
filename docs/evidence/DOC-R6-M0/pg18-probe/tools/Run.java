import java.sql.*; import java.nio.file.*; import java.util.*;
public class Run {
  // usage: Run <user> <db> <file>  ; statements separated by lines containing only ';;'
  public static void main(String[] a) throws Exception {
    Connection c0 = null; try { c0 = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:54329/"+a[1], a[0], ""); } catch (SQLException e) { System.out.println("CONNECT-DENY as "+a[0]+"\n     -> ["+e.getSQLState()+"] "+e.getMessage().lines().findFirst().orElse("")); return; }
    try (Connection c = c0) {
      c.setAutoCommit(true);
      for (String s : Files.readString(Path.of(a[2])).split("(?m)^;;\\s*$")) {
        String t = s.strip(); if (t.isEmpty()) continue;
        String label = t.lines().findFirst().orElse("");
        try (Statement st = c.createStatement()) {
          boolean rs = st.execute(t);
          if (rs) { try (ResultSet r = st.getResultSet()) { StringBuilder b=new StringBuilder(); int n=r.getMetaData().getColumnCount();
              while (r.next()) { for (int i=1;i<=n;i++) b.append(r.getString(i)).append(i<n?" | ":""); b.append("\n"); }
              System.out.println("OK   "+label+"\n     -> "+b.toString().strip().replace("\n","\n        ")); } }
          else System.out.println("OK   "+label);
        } catch (SQLException e) { System.out.println("DENY "+label+"\n     -> ["+e.getSQLState()+"] "+e.getMessage().lines().findFirst().orElse("")); if (!c.getAutoCommit()||true) { try { c.rollback(); } catch(Exception ig){} } }
      }
    }
  }
}
