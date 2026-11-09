package com.lawdesk.deadlines;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.api.PageResponse;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/**
 * The rules, the official holidays and the reminder lead times (LD-054, owner decision D6). They are entered and approved
 * by the owner's specialists; nothing here is seeded and the system never states a period of its own. A rule or a holiday
 * is drafted, approved by a holder of the approval permission (the name and the time are kept) and, if it was wrong,
 * retired; an approved one is never edited or deleted, so earlier proposals stay explainable.
 */
@Service
class DeadlineSettingsService {

    private final DeadlineSettingsStore store;
    private final CurrentActor currentActor;
    private final AuditLog audit;

    DeadlineSettingsService(DeadlineSettingsStore store, CurrentActor currentActor, AuditLog audit) {
        this.store = store;
        this.currentActor = currentActor;
        this.audit = audit;
    }

    private static <T> PageResponse<T> pageOf(List<T> items, int page, int size, long total) {
        return new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
    }

    // ---- rules ---------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    PageResponse<DeadlineRequests.RuleResponse> rules(String status, int page, int size) {
        return pageOf(store.rules(status, size, (long) page * size), page, size, store.countRules(status));
    }

    @Transactional(readOnly = true)
    DeadlineRequests.RuleResponse rule(UUID id) {
        return store.rule(id).orElseThrow(() -> new NotFoundException("deadline rule"));
    }

    @Transactional
    DeadlineRequests.RuleResponse createRule(DeadlineRequests.RuleInput input) {
        ActorRef actor = currentActor.require();
        List<Integer> closed = closedWeekdays(input);
        if (store.ruleKeyTaken(input.code(), input.effectiveFrom(), null)) {
            throw new ConflictException("a rule with this code and effective date exists");
        }
        UUID id = store.insertRule(input, closed, actor.id());
        audit.recordWithChange("DEADLINE_RULE_CREATE", actor.id(), actor.username(), "DEADLINE_RULE", id.toString(), "DRAFT");
        return rule(id);
    }

    @Transactional
    DeadlineRequests.RuleResponse updateRule(UUID id, DeadlineRequests.RuleInput input) {
        ActorRef actor = currentActor.require();
        DeadlineRequests.RuleResponse current = rule(id);
        if (input.version() == null) {
            throw new FieldErrorException("version", "NotNull");
        }
        if (!current.status().equals("DRAFT")) {
            throw new BusinessRuleException(Rules.RULE_NOT_DRAFT, "only a draft rule can be edited");
        }
        List<Integer> closed = closedWeekdays(input);
        if (store.ruleKeyTaken(input.code(), input.effectiveFrom(), id)) {
            throw new ConflictException("a rule with this code and effective date exists");
        }
        if (!store.updateRule(id, input, closed, actor.id())) {
            throw new ConflictException("the rule was changed by someone else");
        }
        audit.recordWithChange("DEADLINE_RULE_UPDATE", actor.id(), actor.username(), "DEADLINE_RULE", id.toString(), "DRAFT");
        return rule(id);
    }

    @Transactional
    DeadlineRequests.RuleResponse approveRule(UUID id, long version) {
        ActorRef actor = currentActor.require();
        DeadlineRequests.RuleResponse current = rule(id);
        if (!current.status().equals("DRAFT")) {
            throw new BusinessRuleException(Rules.RULE_NOT_DRAFT, "only a draft rule can be approved");
        }
        if (!store.approveRule(id, version, actor.id())) {
            throw new ConflictException("the rule was changed by someone else");
        }
        audit.recordWithChange("DEADLINE_RULE_APPROVE", actor.id(), actor.username(), "DEADLINE_RULE", id.toString(),
                "APPROVED");
        return rule(id);
    }

    @Transactional
    DeadlineRequests.RuleResponse retireRule(UUID id, long version) {
        ActorRef actor = currentActor.require();
        DeadlineRequests.RuleResponse current = rule(id);
        if (!current.status().equals("APPROVED")) {
            throw new BusinessRuleException(Rules.RULE_NOT_APPROVED, "only an approved rule can be retired");
        }
        if (!store.retireRule(id, version, actor.id())) {
            throw new ConflictException("the rule was changed by someone else");
        }
        audit.recordWithChange("DEADLINE_RULE_RETIRE", actor.id(), actor.username(), "DEADLINE_RULE", id.toString(),
                "RETIRED");
        return rule(id);
    }

    /** The closed weekdays, ascending and without repeats; a working-day rule needs at least one open weekday. */
    private static List<Integer> closedWeekdays(DeadlineRequests.RuleInput input) {
        for (Integer day : input.closedWeekdays()) {
            if (day == null || day < 1 || day > 7) {
                throw new FieldErrorException("closedWeekdays", "Range");
            }
        }
        List<Integer> closed = DeadlineSettingsStore.sorted(input.closedWeekdays());
        if (input.countMethod() == DeadlineRequests.CountMethod.WORKING && closed.size() >= 7) {
            throw new FieldErrorException("closedWeekdays", "Range");
        }
        return closed;
    }

    // ---- holidays ------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    PageResponse<DeadlineRequests.HolidayResponse> holidays(String status, Integer year, int page, int size) {
        return pageOf(store.holidays(status, year, size, (long) page * size), page, size, store.countHolidays(status, year));
    }

    @Transactional(readOnly = true)
    DeadlineRequests.HolidayResponse holiday(UUID id) {
        return store.holiday(id).orElseThrow(() -> new NotFoundException("official holiday"));
    }

    @Transactional
    DeadlineRequests.HolidayResponse createHoliday(DeadlineRequests.HolidayInput input) {
        ActorRef actor = currentActor.require();
        if (store.holidayDateTaken(input.holidayOn(), null)) {
            throw new BusinessRuleException(Rules.HOLIDAY_DATE_TAKEN, "a holiday is already entered for this date");
        }
        UUID id = store.insertHoliday(input, actor.id());
        audit.recordWithChange("DEADLINE_HOLIDAY_CREATE", actor.id(), actor.username(), "OFFICIAL_HOLIDAY", id.toString(),
                "DRAFT");
        return holiday(id);
    }

    @Transactional
    DeadlineRequests.HolidayResponse updateHoliday(UUID id, DeadlineRequests.HolidayInput input) {
        ActorRef actor = currentActor.require();
        DeadlineRequests.HolidayResponse current = holiday(id);
        if (input.version() == null) {
            throw new FieldErrorException("version", "NotNull");
        }
        if (!current.status().equals("DRAFT")) {
            throw new BusinessRuleException(Rules.HOLIDAY_NOT_DRAFT, "only a draft holiday can be edited");
        }
        if (store.holidayDateTaken(input.holidayOn(), id)) {
            throw new BusinessRuleException(Rules.HOLIDAY_DATE_TAKEN, "a holiday is already entered for this date");
        }
        if (!store.updateHoliday(id, input, actor.id())) {
            throw new ConflictException("the holiday was changed by someone else");
        }
        audit.recordWithChange("DEADLINE_HOLIDAY_UPDATE", actor.id(), actor.username(), "OFFICIAL_HOLIDAY", id.toString(),
                "DRAFT");
        return holiday(id);
    }

    @Transactional
    DeadlineRequests.HolidayResponse approveHoliday(UUID id, long version) {
        ActorRef actor = currentActor.require();
        if (!holiday(id).status().equals("DRAFT")) {
            throw new BusinessRuleException(Rules.HOLIDAY_NOT_DRAFT, "only a draft holiday can be approved");
        }
        if (!store.approveHoliday(id, version, actor.id())) {
            throw new ConflictException("the holiday was changed by someone else");
        }
        audit.recordWithChange("DEADLINE_HOLIDAY_APPROVE", actor.id(), actor.username(), "OFFICIAL_HOLIDAY", id.toString(),
                "APPROVED");
        return holiday(id);
    }

    @Transactional
    DeadlineRequests.HolidayResponse retireHoliday(UUID id, long version) {
        ActorRef actor = currentActor.require();
        if (!holiday(id).status().equals("APPROVED")) {
            throw new BusinessRuleException(Rules.HOLIDAY_NOT_APPROVED, "only an approved holiday can be retired");
        }
        if (!store.retireHoliday(id, version, actor.id())) {
            throw new ConflictException("the holiday was changed by someone else");
        }
        audit.recordWithChange("DEADLINE_HOLIDAY_RETIRE", actor.id(), actor.username(), "OFFICIAL_HOLIDAY", id.toString(),
                "RETIRED");
        return holiday(id);
    }

    // ---- reminder lead times -------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    DeadlineRequests.ReminderSetting reminders() {
        return store.leadDays()
                .map(days -> new DeadlineRequests.ReminderSetting(true, days, store.leadDaysUpdatedAt().orElse(null)))
                .orElseGet(() -> new DeadlineRequests.ReminderSetting(false, null, null));
    }

    @Transactional
    DeadlineRequests.ReminderSetting saveReminders(DeadlineRequests.ReminderInput input) {
        ActorRef actor = currentActor.require();
        for (Integer day : input.leadDays()) {
            if (day == null || day < 0 || day > 365) {
                throw new FieldErrorException("leadDays", "Range");
            }
        }
        List<Integer> days = DeadlineSettingsStore.sorted(input.leadDays());
        if (days.size() > 10) {
            throw new FieldErrorException("leadDays", "Size");
        }
        store.saveLeadDays(days, actor.id());
        audit.recordWithChange("DEADLINE_REMINDERS_SET", actor.id(), actor.username(), "DEADLINE_SETTING", "reminders",
                DeadlineSettingsStore.csv(days));
        return reminders();
    }
}
