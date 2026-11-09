-- LD-050 (WP1d, owner decision D2): the track or stage of a proceeding is separate from the nature of the dispute.
-- Constitutional, enforcement and grievance tracks join the ladder as stages (and the courts or bodies that hear them as
-- levels); appeal and cassation stay stages, never case types. Widening only: no value is rewritten. The rules that
-- order the ladder (an appeal follows a judged first instance, a cassation a judged appeal) are unchanged; the new tracks
-- need a parent proceeding of the same case and no more, because the requirements document gives no further rule.
alter table court drop constraint court_level_known;
alter table court add constraint court_level_known
    check (level in ('FIRST_INSTANCE', 'APPEAL', 'CASSATION', 'CONSTITUTIONAL', 'ENFORCEMENT', 'GRIEVANCE'));
alter table proceeding drop constraint proceeding_stage_known;
alter table proceeding add constraint proceeding_stage_known
    check (stage in ('FIRST_INSTANCE', 'APPEAL', 'CASSATION', 'CONSTITUTIONAL', 'ENFORCEMENT', 'GRIEVANCE'));
-- الدائرة of this stage.
alter table proceeding add column chamber varchar(100)
    constraint proceeding_chamber_visible check (chamber is null or length(btrim(chamber)) > 0);
