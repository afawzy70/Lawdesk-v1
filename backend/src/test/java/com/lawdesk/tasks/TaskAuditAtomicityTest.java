package com.lawdesk.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * QAD-040 (S2): a {@code TASK_STATUS_CHANGED} event with outcome SUCCESS exists only for a status change that was
 * committed. The event is written in the transaction of the change, so every way that transaction can end without a
 * commit (an exception after the audit write, a failure of the commit itself, a lost race) removes the event too.
 * Real PostgreSQL: the commit-time failure is a deferred constraint trigger.
 */
class TaskAuditAtomicityTest extends TaskTestSupport {

    private int auditCount(UUID task) {
        Integer count = jdbc.queryForObject(
                "select count(*) from audit_event where action = 'TASK_STATUS_CHANGED' and target_id = ?",
                Integer.class, task.toString());
        return count == null ? 0 : count;
    }

    private UUID taskOnACase(UUID caseId) throws Exception {
        return create("Atomic audit", caseId, user("Assignee", true), null);
    }

    @Test
    void anExceptionAfterTheAuditWriteLeavesNoSuccessEvent() throws Exception {
        UUID caseId = createCase("Late failure");
        UUID id = taskOnACase(caseId);
        // The first read of the case (the write guard) is real; the second one builds the answer, after the audit write.
        doCallRealMethod().doThrow(new IllegalStateException("answer unavailable")).when(caseLookup).find(caseId);

        mvc.perform(moveTask(id, "DONE", 0)).andExpect(status().isInternalServerError());

        assertThat(statusOf(id)).isEqualTo("OPEN");
        assertThat(auditCount(id)).as("no SUCCESS event for a change that rolled back").isZero();

        Mockito.reset(caseLookup);
        move(id, "DONE", 0);
        assertThat(auditCount(id)).as("the retry records exactly one event").isEqualTo(1);
    }

    @Test
    void aFailureOfTheCommitItselfLeavesNoSuccessEvent() throws Exception {
        UUID id = taskOnACase(null);
        jdbc.execute("""
                create function test_refuse_done_at_commit() returns trigger language plpgsql as $$
                begin raise exception 'refused at commit' using errcode = '23514'; end $$""");
        jdbc.execute("""
                create constraint trigger test_refuse_done after update on task deferrable initially deferred
                for each row when (new.status = 'DONE') execute function test_refuse_done_at_commit()""");
        try {
            mvc.perform(moveTask(id, "DONE", 0)).andExpect(status().is5xxServerError());
            assertThat(statusOf(id)).isEqualTo("OPEN");
            assertThat(auditCount(id)).as("no SUCCESS event when the commit fails").isZero();
        } finally {
            jdbc.execute("drop trigger test_refuse_done on task");
            jdbc.execute("drop function test_refuse_done_at_commit()");
        }

        move(id, "DONE", 0);
        assertThat(auditCount(id)).as("the retry records exactly one event").isEqualTo(1);
    }

    @Test
    void twoSimultaneousChangesRecordExactlyOneEvent() throws Exception {
        UUID id = taskOnACase(createCase("Race"));
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> attempt = () -> {
                go.await();
                return mvc.perform(moveTask(id, "DONE", 0)).andReturn().getResponse().getStatus();
            };
            List<Future<Integer>> results = List.of(pool.submit(attempt), pool.submit(attempt));
            go.countDown();
            List<Integer> statuses = List.of(results.get(0).get(30, TimeUnit.SECONDS),
                    results.get(1).get(30, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(auditCount(id)).isEqualTo(1);
    }

    @Test
    void resendingAnAppliedChangeIsRefusedAndRecordsNothingMore() throws Exception {
        UUID id = taskOnACase(null);
        move(id, "DONE", 0);

        mvc.perform(moveTask(id, "DONE", 0)).andExpect(status().isConflict());

        assertThat(auditCount(id)).isEqualTo(1);
    }
}
