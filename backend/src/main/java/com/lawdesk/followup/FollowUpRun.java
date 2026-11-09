package com.lawdesk.followup;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.notifications.NotificationPublisher;
import com.lawdesk.tasks.TaskFollowUp;

/**
 * The follow-up run (requirements sections 18.3 and 32, decision D11): marks information requests whose reply is late as
 * overdue, marks open tasks past their due date late, and raises the in-app notifications of what needs attention. It never closes anything, sends nothing outside the
 * system and is safe to repeat: a notification exists once per type, record and user.
 */
@Component
public class FollowUpRun {

    /** What one run did. */
    public record Result(int overdueRequests, int notifications, int lateTasks) {
    }

    private static final ZoneId ZONE = ZoneId.of("Asia/Kuwait");

    private final JdbcClient jdbc;
    private final NotificationPublisher notifications;
    private final TaskFollowUp tasks;
    private final DateReminders dates;

    FollowUpRun(JdbcClient jdbc, NotificationPublisher notifications, TaskFollowUp tasks, DateReminders dates) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.tasks = tasks;
        this.dates = dates;
    }

    private record Late(UUID id, UUID caseId, String requestNo, String status, UUID responsible, UUID specialist,
            String caseReferenceNo) {
    }

    @Transactional
    public Result run() {
        LocalDate today = LocalDate.now(ZONE);
        int marked = 0;
        int raised = 0;
        List<Late> late = jdbc.sql("""
                        select r.id, r.case_id, r.request_no, r.status, r.responsible_member_id, c.primary_member_id,
                               c.reference_no
                        from info_request r join legal_case c on c.id = r.case_id
                        where r.due_on < :today and r.status in ('SENT', 'AWAITING', 'PARTIAL', 'INCOMPLETE', 'OVERDUE')
                        order by r.due_on, r.id""")
                .param("today", today)
                .query((rs, n) -> new Late(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                        rs.getString("request_no"), rs.getString("status"),
                        rs.getObject("responsible_member_id", UUID.class), rs.getObject("primary_member_id", UUID.class),
                        rs.getString("reference_no")))
                .list();
        for (Late request : late) {
            if (request.status().equals("SENT") || request.status().equals("AWAITING")) {
                marked += jdbc.sql("""
                                update info_request set status_before_overdue = status, status = 'OVERDUE',
                                    version = version + 1, updated_at = now() where id = :id
                                    and status in ('SENT', 'AWAITING')""")
                        .param("id", request.id()).update();
            }
            for (UUID user : recipients(request.responsible(), request.specialist())) {
                if (notifications.publish(user, NotificationPublisher.INFO_REQUEST_OVERDUE, request.caseId(),
                        "INFO_REQUEST:" + request.id(),
                        Map.of("requestNo", request.requestNo(), "caseReferenceNo", request.caseReferenceNo()))) {
                    raised++;
                }
            }
        }
        List<UUID> withoutDate = jdbc.sql("select id from hearing where follow_up_flag order by hearing_date, id")
                .query(UUID.class).list();
        for (UUID hearing : withoutDate) {
            raised += notifications.publishHearingWithoutDate(hearing, null);
        }
        // LD-056 (WP6): late and due tasks, then the notifications that depend on dates and on what happened.
        TaskFollowUp.Outcome taskOutcome = tasks.run();
        raised += taskOutcome.notifications();
        raised += dates.raise();
        return new Result(marked, raised, taskOutcome.lateTasks());
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
