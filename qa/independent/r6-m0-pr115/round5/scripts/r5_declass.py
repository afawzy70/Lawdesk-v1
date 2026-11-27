import os,sys; sys.path.insert(0,os.environ["TOOLS"])
from common import PEOPLE,run,state,one
from test_two_party import two_person
a2=PEOPLE["a2"]
two_person("POLICY_SET",{"policy":"P-DECLASS","enabled":True},"r6_op_set_policy")
case=run("lawdesk_owner","select case_id::text from lawdesk.complaint_case_link order by recorded_at desc limit 1")[0][0]
print("single-actor declassification with P-DECLASS ON ->", state("lawdesk_legal","select lawdesk.r6_declassify_linked_case(%s,%s)",case,a2),
      "| confidentiality now:", one("select confidentiality from lawdesk.legal_case where id='%s'"%case))
two_person("POLICY_SET",{"policy":"P-DECLASS","enabled":False},"r6_op_set_policy")
