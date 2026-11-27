update lawdesk.deadline set source_type='TEXT', source_id=null, source_text='x', due_on=date '2031-01-01' where source_type='JUDGMENT'
;;
update lawdesk.deadline set source_type='HEARING', source_id='00000000-0000-0000-0000-0000000000e1' where source_type='JUDGMENT'
;;
update lawdesk.deadline set source_id=gen_random_uuid(), due_on=date '2031-01-01' where source_type='JUDGMENT' and case_id='00000000-0000-0000-0000-0000000000c1'
;;
select source_type, source_id is null as no_src, due_on from lawdesk.deadline where case_id='00000000-0000-0000-0000-0000000000c1' order by due_on
;;
select appeal_deadline_id from lawdesk.judgment where id='00000000-0000-0000-0000-0000000000e1'
