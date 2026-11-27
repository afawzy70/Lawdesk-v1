# قرار QA لنشر staging خاص للتحقق — 2026-10-23

توصية مستقلة محدودة: **CONDITIONAL_GO_FOR_PRIVATE_STAGING_VERIFICATION** لكل HEAD أدناه، وليست APPROVE للدمج أوالإصدار العام أوتفعيل CD تلقائي. توصيات merge السابقة BLOCKED محفوظة؛ لم تتحول أي حالة اختبار إلى PASS بالقرار ده.

| PR | exact current tested HEAD | Independent unique PASS / FAIL | private verification recommendation |
|---|---|---:|---|
| #2 | `13297c0a27738fb69f6ad90bf1135678e09b8880` | 85 / 0 | CONDITIONAL GO |
| #5 | `26fbc19fb3e7a5911d6689631a3f46e6e6b3daac` | 144 / 0 | CONDITIONAL GO |
| #7 | `3b6f18cfb9d2f47a6be4dc79803254d748c69a15` | 114 / 0 | CONDITIONAL GO |

GitHub HEADs اتأكدت من جديد وقت التقييم ولم تتغير. الأرقام من التشغيل المستقل السابق لنفس SHAs، مش تشغيل كامل جديد. إعادة إضافية لاختبارات CD19 والـguard10 نجحت عند نفس PR7 في `qa/evidence/head-recheck-20261023T202440Z/`، ومستبعدة من جمع الاختبارات الفريدة. نتائج المطور منفصلة. QAD-007..021 verified fixed؛ لا طلب بتكرار الإصلاحات.

## دليل preflight وحدوده

المالك أفاد: Docker29.9.1 شغال على Contabo، fingerprint مطابق للـrunbook، 265GB متاحة، `/srv/deployments/lawdesk` موجود ومملوك لـdevops؛ البوابة لم تثبت. ده OWNER_REPORTED_PREFLIGHT، مش تنفيذ QA مستقل على السيرفر. منع Docker المحلي المسجل سابقًا يظل حقيقة تخص بيئة QA المحلية، ولا يعني إن Docker السيرفر معطل. لم نفتح SSH أو ننشر أو نغير host.

## شروط قبل أول تشغيل

1. المشغل يثبت gate من المصدر الموافق للـSHA المراجع، root-owned0755 مع parent directories غير قابلة للكتابة بواسطة devops؛ يقارن checksum. يفحص forced-command وrestrict للمفتاح المخصص، ورفض shell/forwarding والطلبات غير الصالحة. لا تضاف صلاحيات Docker جديدة أوsudo عام. Docker membership سلطة قوية على المضيف المشترك؛ gate يقيّد مفتاح CI لكنه ليس عزلًا عن مضيف Docker نفسه.
2. يظل `LAWDESK_STAGING_DEPLOY` غير enabled، ولا manual workflow dispatch. أول تشغيل operator-controlled فقط. لا proxy attach/detach ولا reload/network-connect لأي مكوّن Matbakhy، ولا hostname عام أوhost ports. التحقق من داخل stack/شبكة LawDesk المخصصة فقط، والمخرجات الحساسة لا تنشر.
3. **بوابة حالية مهمة:** gate يرفض SHA غير موجود ضمن ancestry لـorigin/develop (exit5). توصيتنا لا تسمح بتخطي الفحص ولا دمج تلقائي. المالك/المراجع المخول يحدد مسار الدمج المنضبط منفصلًا؛ أي merged SHA مختلف يلزم تسجيله ومراجعة diff وإعادة الفحوص المستقلة المتأثرة وبوابات CI عنده قبل النشر. اختبار كل PR منفصل ليس اختبار تجميعة جديدة.
4. افحص الـrendered Compose للـSHA المختار: project `lawdesk-staging` فقط، volumes/secrets مستقلة، لا host ports أوprivileged أوDocker socket mounts، internal networks. تحقق فعليًا إن `lawdesk-edge` internal وغير موصولة بأي Matbakhy container؛ bootstrap لا يصلح شبكة موجودة بإعداد مختلف. أسرار جديدة وبيانات صناعية فقط، من غير بيانات عملاء أوإنتاج.
5. disk265GB لا يثبت توفر RAM/CPU. سجّل الموارد المتاحة وصحة/IDs/StartedAt/RestartCount وHTTP baseline لـMatbakhy قراءة فقط؛ نافذة مراقبة للبناء لأنه على نفس المضيف، مع إيقاف تجربة LawDesk فقط عند ضغط أوتدهور. لا daemon restart ولاsystem-wide prune ولاحذف volumes.

عدم تحقق أي شرط من دول يجعل تنفيذ المسار المقترح **BLOCKED قبل التشغيل**. gate غير مثبت حاليًا: مسار SSH/CD غير جاهز بعد؛ لا استخدام مفتاح unrestricted كبديل.

## اختبارات بعد التثبيت داخل staging المعزول

real-container startup، nginx/API authorization/readiness، image revision/current-state تطابق SHA، DB role/migrations وrestart/persistence، redeploy idempotence، ثم rollback drill. أول نشر ملوش previous release؛ فشله يتعالج داخل LawDesk فقط، ولا نفترض rollback جاهز. خذ backup من DB الصناعي بعد إنشائه واختبر pg_restore لقاعدة/volume اختبار منفصل، وسجّل failed-start rollback وإعادة تشغيل الصورة السابقة ضدschema الحالي. smoke وحده ليس إثبات restore.

دي تظل BLOCKED/NOT_RUN لحد ظهور أدلة فعلية بالـSHA والبيئة. إثبات عدم تأثر Matbakhy يتطلب مقارنة before/after فعلية؛ مراجعة الكود والفحوص المحلية لا تثبته وحدها. proxy الحقيقي يظل NOT_RUN وخارج الإذن الحالي.

مش كل المتبقي operational-only: QA-0004/0037 حماية الفرع/الحوكمة، وWebKit/manual screenreader ما زالوا BLOCKED/NOT_RUN. هما لا يمنعوا التجربة الداخلية ذات البيانات الصناعية والشروط المذكورة، لكن لم يتم حلهم أوإسقاطهم. public release وautomatic CD والدمج العام ما زالوا غير معتمدين؛ يحتاجوا قرار QA مستقل بعد الأدلة التشغيلية ومعالجة مخاطر الإصدار.

## الخلاصة التنفيذية

مفيش confirmed unresolved code defect في النطاق المنفذ يمنع private staging verification بشروطه. مسموح كتوصية QA مشروطة للمشغل المخول، لا تنفيذ بواسطة QA. لا ضمان لعزل host-level من Compose وحده؛ الاحتياطات والمراقبة لازمة على المضيف المشترك. الأدلة السابقة وموانعها محفوظة في `qa/reports/current-review-index.json` و`qa/evidence/current-runtime-scope-status.json` بلا تعديل تصنيفاتها.
