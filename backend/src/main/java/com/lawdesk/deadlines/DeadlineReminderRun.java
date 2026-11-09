package com.lawdesk.deadlines;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.notifications.NotificationPublisher;

/**
 * The in-app reminders of the legal deadlines (requirements sections 10 and 32, decisions D6 and D11). It only raises
 * notifications: it never completes, misses or closes a deadline and sends nothing outside the system. It is safe to repeat
 * (a notification exists once per type, record and user).
 *
 * <ul>
 *   <li><b>Approaching</b>: for an open, approved deadline whose due date is within the configured lead days, one notification
 *       per lead day that has been reached. The document gives no lead times, so there is no default: until somebody
 *       configures them, no approaching reminder is raised.</li>
 *   <li><b>Overdue</b>: for an open, approved deadline past its due date. It needs no lead time, so it does not wait for one.</li>
 * </ul>
 *
 * A deadline nobody approved yet (manual or proposed) is not an official date and raises nothing. The recipients are the
 * responsible member and the case's specialist.
 */
@Component
public class DeadlineReminderRun {

    public static final String APPROACHING = "DEADLINE_APPROACHING";
    public static final String OVERDUE = "DEADLINE_OVERDUE";

    private static final ZoneId ZONE = ZoneId.of("Asia/Kuwait");

    private final JdbcClient jdbc;
    private final DeadlineSettingsStore settings;
    private final NotificationPublisher notifications;

    DeadlineReminderRun(JdbcClient jdbc, DeadlineSettingsStore settings, NotificationPublisher notifications) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.notifications = notifications;
    }

    private record Open(UUID id, UUID caseId, LocalDate dueOn, String action, UUID owner, UUID specialist, String referenceNo) {
    }

    @Transactional
    public DeadlineRequests.ReminderRunResult run() {
        LocalDate today = LocalDate.now(ZONE);
        List<Integer> leads = settings.leadDays().orElse(null);
        int approaching = 0;
        int overdue = 0;
        if (leads != null && !leads.isEmpty()) {
            int widest = leads.get(leads.size() - 1);
            for (Open d : open("d.due_on between :today and :last", today, today.plusDays(widest))) {
                long remaining = ChronoUnit.DAYS.between(today, d.dueOn());
                int lead = leads.stream().filter(l -> l >= remaining).findFirst().orElse(widest);
                for (UUID user : recipients(d.owner(), d.specialist())) {
                    if (notifications.publish(user, APPROACHING, d.caseId(), "DEADLINE:" + d.id() + ":" + lead,
                            Map.of("caseReferenceNo", d.referenceNo(), "action", d.action(), "dueOn", d.dueOn().toString(),
                                    "daysRemaining", String.valueOf(remaining)))) {
                        approaching++;
                    }
                }
            }
        }
        for (Open d : open("d.due_on < :today", today, today)) {
            for (UUID user : recipients(d.owner(), d.specialist())) {
                if (notifications.publish(user, OVERDUE, d.caseId(), "DEADLINE:" + d.id(),
                        Map.of("caseReferenceNo", d.referenceNo(), "action", d.action(), "dueOn", d.dueOn().toString()))) {
                    overdue++;
                }
            }
        }
        return new DeadlineRequests.ReminderRunResult(leads != null && !leads.isEmpty(), approaching, overdue);
    }

    private List<Open> open(String window, LocalDate today, LocalDate last) {
        return jdbc.sql("""
                        select d.id, d.case_id, d.due_on, d.action, d.owner_id, c.primary_member_id, c.reference_no
                        from deadline d join legal_case c on c.id = d.case_id
                        where d.status = 'OPEN' and d.calc_status = 'APPROVED' and d.due_on is not null and """ + " " + window
                        + " order by d.due_on, d.id")
                .param("today", today).param("last", last)
                .query((rs, n) -> new Open(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                        rs.getObject("due_on", LocalDate.class), rs.getString("action"),
                        rs.getObject("owner_id", UUID.class), rs.getObject("primary_member_id", UUID.class),
                        rs.getString("reference_no")))
                .list();
    }

    private static Set<UUID> recipients(UUID... users) {
        Set<UUID> out = new LinkedHashSet<>();
        for (UUID user : users) {
            if (user != null) {
                out.add(user);
            }
        }
        return out;
    }
}
