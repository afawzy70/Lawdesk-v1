# PR #7 — مراجعة staging CD

التوصية **REQUEST_CHANGES** عند SHA `32724ae88983ac72a43d49d7f3a8db3e253a989e`.

اتنفذ **100 assertion مستقل: 94 PASS / 6 FAIL**. منهم84 regression للتطبيق/السياسة/SQL/Compose/React/Vite، و16 CD failure/security probes بنتيجة10/6. اختبارات المطوّر اللي اتعادت منفصلة: backend92، frontend50، tools50، deploy94 كلها PASS. job النشر في CI كان SKIPPED/NOT_RUN، ومفيش نشر حصل في المراجعة.

التجربة استخدمت Git محلي حقيقي وملفات/مفاتيح Ed25519 مؤقتة، ونفس gate/bootstrap/deploy/proxy scripts وخطوة validation من workflow. كل Docker/curl/gh transport المستهدف اتحوّل إلى doubles مسجلة؛ مفيش SSH للمضيف ولا اتصال بـMatbakhy. الاختبارات دي لا تثبت runtime permissions أونجاح deployment حقيقي.

| ID | الخطورة | الإخفاق المؤكد |
|---|---|---|
| QAD-016 | High | manual dispatch بيقبل merged SHA من غير أي طلب للتحقق من exact-SHA CI. fixture لCI فاشل مرّ بـexit0 وskip=false؛ automatic caller عندهneeds صحيحة. |
| QAD-017 | High | صور cached عليها revision مختلف اتقبلت لمجرد نجاح image inspect، واتنادى deploy من غير rebuild أورفض. current-release state مش شهادة لهوية الصورة. |
| QAD-018 | High | bootstrap بيرجع0 لو نفس CI public key الصالح موجود قبل كده بلاrestrict، ويسيب entry غير مقيّد. الشرط هو إعادة استخدام مفتاح غير مقيّد؛ مش إثبات إن المضيف الحالي متأثر. |
| QAD-019 | Medium | reload ناجح ثمpersist-copy فاشل يسيب active config جديدة وملفLIVE قديم، رغم رسالة إن السابق لسه مستخدم. |
| QAD-020 | High | BEGIN غير متوازن ممكن يشيل host موجود، والـguard يمر والـattach يرجع0. فحصhosts ما غطاشliteral hostname فيfixture. |
| QAD-021 | Medium | rollback up يستخدمmanifestA، لكنsmoke verification يستخدمmanifestB الخاص بالإصدار الفاشل معtagA. ده غيرعيبsame-release القديم اللي اتصلح. |

تفاصيل preconditions وبيانات وخطوات وexpected/actual والـassertion IDs في `qa/defects/staging-cd-findings.json`. الدليل التنفيذي `qa/evidence/pr-7-32724ae88983/valid-key-retest/independent-cd-results.json`، والكود `qa/independent/cd_review.py`.

## SSH وCI والصور

4 محاولات command injection/extra commands اترفضوا قبل أي Dockercall. status control نجح، وcommit حقيقي من branch غير مدموجة اترفض بعدfetch للـdevelop المحلي. دي أدلة إيجابية لparser وancestry، لكنها لا تثبت إن authorized_keys الفعلي أوsshd أوroot-owned gate اتثبتوا صح.

QAD-018 اتعاد بمفتاحEd25519 صالح مولّد مؤقتًا؛ مفتاح خاص ما اتنشرش. نسخة الاختبار الأولى استخدمتplaceholder public key، ومحفوظة كدليل تشخيصي؛ التجميع بيستخدم إعادة الاختبار بالمفتاح الصالح فقط.

المسار الأوتوماتيكي عنده6needs وشرطpush/develop/repository صحيح. المسار اليدوي بيكتفي بالـancestry. معB4 وعدم server enforcement، وجودcommit علىdevelop مش بديل عن نجاحCI لنفسSHA. لازمgate واضح أوattestation موثوق، أوقرارمنتج صريح لأي استثناء؛ مفيش استثناء معتمد في أدلة المراجعة.

الصور المحلية بتتبني بـrevision label، لكن الصورة الموجودة مسبقًا مش بيتراجع label/digest بتاعها. اختبار QAD-017 بيحاكي tag قديم أوغلط؛ مش ادعاء إن حد اتلاعب بصورة حقيقية على المضيف.

## backup والـrollback

اللي نجح مستقلًا: فشل pg_dump يمنع up، يحافظ على current-release، وينضف partial. ملف dump بيتكتب0600، وretention بيحتفظ بأحدث10 dumps وبيسيب الملف غير التابع. البيانات في transport صناعية، فمش بندّعي اختبار archive صالح أوpg_restore؛ restore/recovery الحقيقي NOT_RUN.

الـrollback بيحتفظ بالـvolumes ومفيش schema downgrade تلقائي. لكن ضمان تشغيل صورة قديمة على schema جديدة لسه محتاج real artifact/schema drill؛ تجاهل Flyway future versions لوحده مش إثبات لتوافق استعلامات التطبيق القديم. QAD-021 مثبت من trace لمسارات Compose، حتى لو خدمات A/B متماثلة حاليًا.

## Matbakhy والـproxy

الـauto gate/deploy paths اللي اتراجعت بتحدد project باسم `lawdesk-staging`. لكن PR7 بيشحن attach/detach بيعملوا docker cp وnetwork connect/disconnect وcaddy reload على proxy Matbakhy. بالتالي **مفيش أساس لإعلان إن الاقتراح كله لا يعدل Matbakhy**؛ التعديل مقصود فيrunbook ومحتاج مسار آمن وتفويض تشغيلي منفصل.

في QAD-020 استخدمنا ملفين مصطنعين فقط. Caddy2.10.2 الرسمي، بعد مطابقة SHA512 المنشور وSHA256 من GitHub asset API، عمل adapt للملف الأصلي والمرشح:

- الأصلي فيه `app.qa.invalid` و`api.qa.invalid`.
- المرشح فيه `app.qa.invalid` و`lawdesk.qa.invalid`؛ host الـapi اختفى.

الملفان صالحين نحويًا. ده دعم فعلي لإثبات إن syntax validation مش فحص لعدم إزالة hosts. الدليل في `caddy-syntax/native-caddy-adapt.json`. مفيش listener اشتغل، ومفيش ملف حقيقي لـMatbakhy اتقرا أواتعدل.

QAD-019 كمان بيثبت إن reload/persist مش عملية atomic؛ لازم مسار restore يرجع الحالتين ويتحقق منهم. ضمان عدم restart للـcontainer لوحده مش ضمان عدم قطع routing.

حساب devops فيrunbook عنده صلاحيات Docker daemon واسعة. اسم مشروع Compose مش حد أمني يفصل Matbakhy. المخاطرة دي لازم تفضل ظاهرة وتتحسم تشغيليًا؛ QA ما عملش grants أوتعديل user/group/daemon.

## غير المنفذ والحدود

Docker socket مازال permission denied في الفحص الجديد. real-container stack وinstalled SSH gate وactual image identity وbackup restore وlive rollback BLOCKED/NOT_RUN. تشغيل shared proxy الحقيقي NOT_RUN لأن المهمة لا تفوض تعديل Matbakhy. نجاح فحوص المطوّر فيCI منفصل عن التحقق المستقل.

QAD-001..006 الأساسية لسه OPEN، وQAD-007..015 اتوثقت إصلاحاتهم عند الـHEADs المنطبقة. لا دمج ولا تفعيل CD قبل إصلاح الستة الجدد وإعادة الاختبارات عند HEAD جديد. التعليق المبكر لـClaude: https://github.com/afawzy70/Lawdesk-v1/pull/7#issuecomment-6371326865.
