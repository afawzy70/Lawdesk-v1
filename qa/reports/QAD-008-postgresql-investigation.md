# QAD-008 — نتيجة تحقيق PostgreSQL

الحالة **OPEN**، الخطورة **Medium — data integrity**. الـSHA المعاد اختباره: `c0da6910884a7e85ee17e80a78416aac51de216c`.

الوصف الدقيق: القيمة `app_release.version` اللي بتسجل إصدار التطبيق بتقبل whitespace فقط في قاعدة البيانات. الجدول اتعمل بواسطة `V1__app_release.sql`؛ العيب **مش في اسم ملف Flyway أو رقم الـmigration**.

الأثر الحالي محصور في سلامة بيانات تتبّع الإصدار/بدايات التشغيل؛ مفيش دليل على تسريب بيانات أو تجاوز تفويض، ومش بنصنفه Critical. العقد المقترح LD-004 AC3 بيطلب تطابق قواعد blank بين `@NotBlank` وقيد PostgreSQL، وده مش حاصل.

البيئة: Java21، Spring Boot4.1.2، PostgreSQL18.6 حقيقي، قاعدة مؤقتة من QA، نفس migrations الأصلية، وملف اختبارات QA مستقل فقط. مفيش تعديل لكود المنتج، ولا تشغيل على DB مشتركة.

خطوات الإعادة في QA فقط:

1. أنشئ DB PostgreSQL18.6 مؤقتة وطبّق migrations الخاصة بالـSHA.
2. نفذ التالي في الـschema اللي اتطبقت فيه migration. الاختبارات المدمجة تستخدم `public`؛ stack يستخدم `lawdesk`، فغيّر اسم الـschema فقط عند إعادة الاختبار داخل stack معزول.

```sql
BEGIN;
INSERT INTO public.app_release(version) VALUES (E'\t');
SELECT length(version), encode(convert_to(version, 'UTF8'), 'hex') AS utf8_hex
FROM public.app_release WHERE version = E'\t';
ROLLBACK;
```

المتوقع: رفض INSERT بقيد صحة البيانات، وعدم حفظ صف.
الفعلي: INSERT نجح، وSELECT رجّع `length=1` و`utf8_hex=09`.

| المدخل | length الفعلية | UTF8 hex الفعلي |
|---|---:|---|
| tab `E'\t'` | 1 | `09` |
| newline `E'\n'` | 1 | `0a` |
| CRLF `E'\r\n'` | 2 | `0d0a` |
| U+2003 EM SPACE | 1 | `e28083` |

نتيجة الاختبارات المستقلة: نفس7اختبارات SQL الحالية، **3 PASS / 4 FAIL**، بدون مضاعفة العدد عند إعادة التشغيل. حد64/65، تاريخ migration، وrollback transaction نجحوا. الأربع whitespace assertions فشلت؛ `@AfterEach` نظّف صفوف QA.

سبب العيب: `length(trim(version)) > 0` بيزيل المسافات العادية، فمش مكافئ لمفهوم blank في طبقة التطبيق. إضافة رفض tab فقط مش كفاية؛ الإصلاح مسؤولية Claude، مع مراعاة البيانات القديمة وترقية schema وعدم كسر checksums الخاصة بـFlyway.

أمر الإعادة: انسخ نفس `qa/independent/java/IndependentDatabaseAcceptanceTest.java` إلى نسخة QA المؤقتة، وشغّل Maven wrapper مع `-Dtest=IndependentDatabaseAcceptanceTest test` وJava21 وcache QA. التفاصيل في `qa/independent/CONTINUATION-EXECUTION.md`.

الأدلة: `qa/evidence/pr-2-c0da6910884a/independent-database-diagnostics.json` و`independent-database-diagnostics.log`. الرسائل فيها نتيجة SQL الفعلية بالـlength والـhex. سجل التحقيق القديم محفوظ في أدلة الـSHAs السابقة؛ العيب يفضل OPEN لحد إصلاحه وإعادة نفس الاختبار على HEAD الجديد.
