# PR #5 — مراجعة preview المستقلة

التوصية **REQUEST_CHANGES** عند SHA `ad0def0aeba514619f060fe1f150d1833a9f3fd9`.

PR: https://github.com/afawzy70/Lawdesk-v1/pull/5. معتمد على PR #2، اللي لسه فيه عيب High في بوابة smoke وعيوب مؤكدة تانية. مش موصى بدمج أي PR منهم قبل الإصلاح والتحقق المستقل.

الاختبارات المستقلة: **62 PASS / 8 FAIL من 70 assertion**. منهم 64 فحص layout/accessibility/navigation/labels بنتيجة 56/8، و6 فحوص أمن متصفح بنتيجة 6/0. اختبارات المطوّر منفصلة: **136 PASS**، وlint/typecheck/coverage/build نجحوا عند نفس SHA.

البيئة: build فعلي من الكود المثبت؛ Chromium headless build1248 مع Playwright1.64.1 وaxe4.14.0 وNode22.23.3. اللغات ar-EG وen-GB، والتوقيت Africa/Cairo، والمقاسات 320/375/767/768/1024/1366. صور 375/1366px في اللغتين تحت `qa/evidence/pr-5-ad0def0aeba5/browser/`.

الخادم fixture محلي للـSPA، بيطبق CSP المقروء من nginx config، والـhealth503/401 ردود synthetic. الاختبارات بتقيس المتصفح والكود الفعلي تحت السياسة دي، مش نشر أوnginx حقيقي أوSpring authentication. الأدلة في `independent-browser-results.json` و`independent-browser-security.json`؛ كل assertion فيها خطوات وexpected/actual وpriority وتتبّع.

## اللي اتأكد مستقلًا

- مفيش document horizontal overflow في 12 تركيبة لغة/عرض، وجدول desktop بيلائم الحاوية. جدول الموبايل فيه scrolling داخل حاويته؛ مشكلة الوصول ليه موضحة تحت.
- RTL/LTR وlang مضبوطين؛ sidebar في RTL اتراجع بصريًا، والأرقام والتواريخ العربية ظاهرة. لغة URL بتستمر بعد reload/navigation.
- اللافتة الدائمة وPreview في عنوان كل 9 routes باللغتين. الثلاث مناطق بيانات عليها badge وaccessible description واضحين. الأسماء والمحاكم اللي اتراجعت نموذجية؛ مفيش ادعاء إن ميزة القضايا شغالة.
- skip link بيتلقى أول Tab، وEnter بينقل focus للـmain. Escape من nav نفسها بيقفل ويرجع التركيز للزر.
- مفيش forms/inputs/login/save/create workflow، ولا storage credentials. الطلبات المسجلة GET ولـsame-origin فقط.
- مدخل locale فيه SVG/onload ما اتنفذش وما انعكسش، وfallback للإنجليزي صحيح. تفاصيل 401 الخاصة بعلامة الاختبار ما ظهرتش، وحالة unavailable مترجمة. ده مش اختبار نفاذية شامل أو تفويض backend.
- أخطاء console المسجلة كلها 503 المقصودة من fixture؛ مفيش CSP violations أوJavaScript exception جديدة في الجولة.

## العيوب والحدود

**QAD-014 — Medium، confirmed:** افتح Menu عند 375px واضغط Escape فورًا مع بقاء focus على زر Menu. القائمة تفضل مفتوحة وaria-expanded=true. اتكرر في 320/375/767 وباللغتين: 6 إخفاقات. مراجعة المصدر بتوضح إن keydown handler على nav فقط؛ الإغلاق الموصوف في PREVIEW مش بيغطي حالة الزر. المطلوب إصلاح وتغطية regression؛ QA ما عدلش التنفيذ.

**QAD-015 — Medium، automated accessibility finding:** axe أبلغ عن `scrollable-region-focusable` بخطورة serious على `.table-scroll` في 375px باللغتين. الحاوية من غير tabIndex ومفيهاش focusable descendants؛ جزء التاريخ/الوقت خارج العرض. تقرير axe بيحدد خطر Safari keyboard. **WebKit/Safari وmanual screen-reader NOT_RUN**، فمش بندّعي قياس فشل Safari فعليًا. تشخيص programmatic focus في Chrome محفوظ في تقرير security، ومينفعش نستنتج منه شهادة للمتصفحات التانية.

المخاطر الباقية: Firefox/WebKit، zoom400%، dark/forced-colors، screen-reader يدوي، ومراجعة ترجمة متخصصة NOT_RUN. رسالة ErrorBoundary بالإنجليزي والتواريخ الثابتة حدود معلنة في PREVIEW. الـpreview مش قبول لقصص LD-035/036/037. real nginx/CSP delivery والـreal-container stack المستقل لسه BLOCKED؛ المراجعة ما بتحوّلش نجاح CI المطوّر إلى PASS من QA.

التعليق المنشور للإصلاح: https://github.com/afawzy70/Lawdesk-v1/pull/5#issuecomment-6369072142. الأدلة القديمة محفوظة، ومفيش دمج أو نشر أوapproval. أي HEAD يتغير يحتاج snapshot وأدلة جديدة، ونفس الاختبارات تتكرر من غير تغيير expected لتجاوز العيوب.
