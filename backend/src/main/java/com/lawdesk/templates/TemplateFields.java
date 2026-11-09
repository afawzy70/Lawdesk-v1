package com.lawdesk.templates;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The fields of the case file a template may use as {@code {{placeholders}}} (LD-057, requirements section 29), and their
 * values for one case. A field the case file does not hold has no value: a draft then keeps the placeholder and names the
 * field as missing, and nothing is ever guessed. Amounts are each their own field with their own label (owner decision D5).
 */
@Component
class TemplateFields {

    /** A placeholder token: two braces, a dotted name, two braces; spaces inside the braces are allowed. */
    static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([A-Za-z][A-Za-z0-9_.]*)\\s*}}");

    private static final ZoneId KUWAIT = ZoneId.of("Asia/Kuwait");

    record Field(String key, String labelAr, String labelEn) {
    }

    private static final List<Field> FIELDS = List.of(
            new Field("case.referenceNo", "الرقم الداخلي للقضية", "Internal case number"),
            new Field("case.automatedNo", "الرقم الآلي القضائي", "Automated judicial number"),
            new Field("case.title", "عنوان القضية", "Case title"),
            new Field("case.type", "نوع النزاع", "Dispute type"),
            new Field("case.subject", "موضوع الدعوى", "Subject of the claim"),
            new Field("case.openedOn", "تاريخ فتح القضية", "Date the case was opened"),
            new Field("case.receivedOn", "تاريخ ورود القضية للإدارة", "Date the case reached the department"),
            new Field("case.status", "حالة القضية", "Case status"),
            new Field("case.chamber", "الدائرة", "Chamber"),
            new Field("case.facts", "وقائع القضية", "Facts of the case"),
            new Field("case.authorityPosition", "صفة الجهة في الدعوى", "Position of the authority"),
            new Field("case.claimedAmount", "المبلغ المطالب به", "Amount claimed"),
            new Field("case.financialAmount", "الأثر المالي", "Financial impact"),
            new Field("case.primaryMember", "العضو القانوني المختص", "Specialist legal member"),
            new Field("case.alternateMember", "العضو القانوني البديل", "Alternate legal member"),
            new Field("parties.clients", "الجهة الموكِّلة", "The authority (client)"),
            new Field("parties.opponents", "الخصوم", "Opponents"),
            new Field("court.name", "المحكمة", "Court"),
            new Field("proceeding.stage", "درجة التقاضي", "Stage of litigation"),
            new Field("proceeding.number", "رقم القضية بالمحكمة", "Court case number"),
            new Field("hearing.next.date", "موعد الجلسة القادمة", "Next hearing date"),
            new Field("today", "تاريخ اليوم", "Today's date"));

    private static final Map<String, Field> BY_KEY = new LinkedHashMap<>();

    static {
        FIELDS.forEach(f -> BY_KEY.put(f.key(), f));
    }

    private static final Map<String, String> CASE_TYPE = Map.of("CIVIL", "مدني", "CRIMINAL", "جنائي", "COMMERCIAL", "تجاري",
            "LABOR", "عمالي", "FAMILY", "أحوال شخصية", "ADMINISTRATIVE", "إداري", "CONSTITUTIONAL", "دستوري", "OTHER", "أخرى");
    private static final Map<String, String> CASE_STATUS = Map.of("OPEN", "مفتوحة", "ACTIVE", "متداولة", "SUSPENDED", "موقوفة",
            "CLOSED", "منتهية", "ARCHIVED", "مؤرشفة");
    private static final Map<String, String> STAGE = Map.of("FIRST_INSTANCE", "أول درجة", "APPEAL", "الاستئناف",
            "CASSATION", "النقض", "CONSTITUTIONAL", "الدستورية", "ENFORCEMENT", "التنفيذ", "GRIEVANCE", "التظلم");

    private final JdbcClient jdbc;

    TemplateFields(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    static List<Field> all() {
        return FIELDS;
    }

    static boolean known(String key) {
        return BY_KEY.containsKey(key);
    }

    static Field field(String key) {
        return BY_KEY.get(key);
    }

    /** The distinct placeholder keys of a text, in order of first appearance. */
    static List<String> keysIn(String body) {
        List<String> keys = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(body);
        while (matcher.find()) {
            if (!keys.contains(matcher.group(1))) {
                keys.add(matcher.group(1));
            }
        }
        return keys;
    }

    /** The values the case file holds, by key; a field with no value is absent from the map. */
    Map<String, String> valuesOf(UUID caseId) {
        Map<String, String> values = new LinkedHashMap<>();
        jdbc.sql("""
                        select c.reference_no, c.automated_no, c.title, c.case_type, c.subject, c.opened_on, c.received_on,
                               c.status, c.chamber, c.facts, c.authority_position, c.claimed_amount, c.financial_amount,
                               p.display_name as primary_name, a.display_name as alternate_name
                        from legal_case c left join app_user p on p.id = c.primary_member_id
                        left join app_user a on a.id = c.alternate_member_id where c.id = :id""")
                .param("id", caseId).query((rs, n) -> {
                    put(values, "case.referenceNo", rs.getString("reference_no"));
                    put(values, "case.automatedNo", rs.getString("automated_no"));
                    put(values, "case.title", rs.getString("title"));
                    put(values, "case.type", CASE_TYPE.get(rs.getString("case_type")));
                    put(values, "case.subject", rs.getString("subject"));
                    put(values, "case.openedOn", date(rs, "opened_on"));
                    put(values, "case.receivedOn", date(rs, "received_on"));
                    put(values, "case.status", CASE_STATUS.get(rs.getString("status")));
                    put(values, "case.chamber", rs.getString("chamber"));
                    put(values, "case.facts", rs.getString("facts"));
                    put(values, "case.authorityPosition", position(rs.getString("authority_position")));
                    put(values, "case.claimedAmount", money(rs.getBigDecimal("claimed_amount")));
                    put(values, "case.financialAmount", money(rs.getBigDecimal("financial_amount")));
                    put(values, "case.primaryMember", rs.getString("primary_name"));
                    put(values, "case.alternateMember", rs.getString("alternate_name"));
                    return Boolean.TRUE;
                }).list();
        put(values, "parties.clients", partyNames(caseId, "CLIENT"));
        put(values, "parties.opponents", partyNames(caseId, "OPPONENT"));
        jdbc.sql("""
                        select ct.name_ar, p.stage, p.court_case_number, p.court_case_year
                        from proceeding p join court ct on ct.id = p.court_id where p.case_id = :id
                        order by p.filing_date desc, p.created_at desc limit 1""")
                .param("id", caseId).query((rs, n) -> {
                    put(values, "court.name", rs.getString("name_ar"));
                    put(values, "proceeding.stage", STAGE.get(rs.getString("stage")));
                    put(values, "proceeding.number", rs.getString("court_case_number") + " لسنة " + rs.getInt("court_case_year"));
                    return Boolean.TRUE;
                }).list();
        LocalDate today = LocalDate.now(KUWAIT);
        List<String> next = jdbc.sql("""
                        select min(hearing_date)::text from hearing where case_id = :id and status = 'SCHEDULED'
                        and hearing_date >= :today""").param("id", caseId).param("today", today).query(String.class).list();
        put(values, "hearing.next.date", next.isEmpty() ? null : next.get(0));
        put(values, "today", today.toString());
        return values;
    }

    private String partyNames(UUID caseId, String role) {
        List<String> names = jdbc.sql("""
                        select p.name from case_party cp join party p on p.id = cp.party_id
                        where cp.case_id = :id and cp.role = :role order by cp.created_at, p.name""")
                .param("id", caseId).param("role", role).query(String.class).list();
        return names.isEmpty() ? null : String.join("، ", names);
    }

    private static void put(Map<String, String> values, String key, String value) {
        if (value != null && !value.isBlank()) {
            values.put(key, value);
        }
    }

    private static String date(ResultSet rs, String column) throws java.sql.SQLException {
        LocalDate date = rs.getObject(column, LocalDate.class);
        return date == null ? null : date.toString();
    }

    private static String position(String code) {
        if (code == null) {
            return null;
        }
        return "PLAINTIFF".equals(code) ? "مدعية" : "مدعى عليها";
    }

    private static String money(BigDecimal amount) {
        return amount == null ? null : amount.setScale(3, java.math.RoundingMode.UNNECESSARY).toPlainString() + " دينار كويتي";
    }
}
