# مراجعة PR رقم <number>

- رابط PR / القصص / معايير القبول:
- base SHA / head SHA / آخر تحقق من head:
- SHA المختبر / هل الشجرة فيها تغييرات / artifact digest:
- البيئة: OS، Java، Spring، Node، browser، PostgreSQL، migration history، seed digest:
- نطاق التفويض وبيئة QA المعزولة:
- دليل RED (commit، command، failed assertion، log):
- دليل GREEN وREFACTOR (commit، command، counts، log):
- مراجعة التنفيذ والمعمارية ومصفوفة الصلاحيات:
- نتائج مستقلة: PASS / FAIL / BLOCKED / NOT_RUN؛ tooling منفصل:
- لكل فشل: case ID، expected/actual، failed assertion، خطوات إعادة الإنتاج، evidence path:
- defects وروابطها، الخطورة، أثرها على الإصدار:
- حالات غير منفذة وسببها ومخاطر باقية:
- التوصية: APPROVE / REQUEST_CHANGES / BLOCKED، وسببها:

لا تسجل PASS من وجود test file أو skipped test. لا تعتمد أو تدمج PR غير متحقق منه.
