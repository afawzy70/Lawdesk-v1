-- LD-058 (WP8): the performance run at 50 000 cases showed the planner choosing a nested loop that compared every hearing
-- with every visible case (46 million comparisons, 12 s) because it priced the access function at the default cost of 100
-- for every row. CaseAccessPolicy now puts column tests before the function (it runs only for cases reached by a unit
-- scope, a grant or a task), so its declared cost is lowered to match. This changes plans only, never a result; the rule
-- itself (the function bodies) is untouched. If a later migration replaces either function with `create or replace`, state
-- the cost again there, or the planner returns to the default.
alter function case_visible(uuid, uuid, boolean, text, text) cost 10;
alter function case_in_scope(text, text, uuid, boolean, text, text) cost 5;

-- The tasks a user is assigned, by case: read once per query to see which cases a user reaches through a task.
create index task_assignee_case on task (assignee_id, case_id) where case_id is not null;
