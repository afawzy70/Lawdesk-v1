# مطابقة الوثيقة الأصلية بالنص المعتمد في المستودع

أُجريت في 2026-11-10 على الملف الذي أرفقه المالك في الجلسة (لا يُرفع الملف إلى المستودع). تتيح هذه الوثيقة لـCodex إعادة التحقق بشكل مستقل متى توفر له الملف.

| البند | القيمة |
|---|---|
| الملف | «نظام لو ديسك.docx» |
| SHA-256 للملف | `edf402d18419a10e1a9012144f74e1b19c9b261a2d487a1ec84c32083bf72d3f` (مطابق للمسجل في `SOURCE.md`) |
| الحجم | 48,599 بايت |
| `word/document.xml` | 445,643 بايت، SHA-256 `ef6d403c3deb88141a0eaaee97e045a2a15671a6d9c2f5ef0025a11d62e0eb87` |
| محتويات الحزمة | `[Content_Types].xml`, `_rels/.rels`, `word/{document,settings,numbering,styles,webSettings,fontTable}.xml`, `word/_rels/document.xml.rels`, `word/theme/theme1.xml`, `docProps/{core,app}.xml`؛ لا `media/` ولا `comments.xml` ولا `footnotes.xml` |
| أداة الاستخراج | `docs/requirements/extract_docx.py` (بايثون القياسي فقط)، SHA-256 `b0025b92087fd9942a7aa2224d8cd9b518ffcbce2622c91539574631c6c67961` |
| أمر الاستخراج | `python3 -I extract_docx.py <الملف.docx> > مخرج.txt` |
| ناتج الاستخراج | 791 سطرًا غير فارغ، SHA-256 `40d371be0d852833a4625460908e7b2f280dfe6f2038ea514c1d9a7750f1dee3` |
| النص المعتمد في المستودع | `docs/requirements/lawdesk-comprehensive-source.txt`: 791 سطرًا، SHA-256 `40d371be0d852833a4625460908e7b2f280dfe6f2038ea514c1d9a7750f1dee3` |
| النتيجة | `cmp` بين ناتج الاستخراج الجديد والنص المعتمد: **متطابقان بايتًا ببايت** |

## ما يثبته هذا وما لا يثبته

* يثبت أن النص المعتمد مستخرج من هذا الملف بعينه (بصمة الملف وبصمة الناتج) وأن الاستخراج قابل للتكرار.
* لا يثبت أن الاستخراج التقط كل ما يظهر في Word: الأداة تقرأ `word/document.xml` فقط؛ لا جداول في الوثيقة ولا صور ولا تعليقات بحسب فحص المحتويات. أي محتوى ضمن مربعات نص أو رؤوس وتذييلات غير موجود في الحزمة (لا `header*.xml` ولا `footer*.xml`).
* هذا التحقق من المطوّر، وقبول المطابقة لـCodex.

## إعادة التحقق (Codex)

```bash
sha256sum "نظام لو ديسك.docx"                                # = edf402d1…72d3f
unzip -p "نظام لو ديسك.docx" word/document.xml | sha256sum   # = ef6d403c…eb87
python3 -I docs/requirements/extract_docx.py "نظام لو ديسك.docx" > /tmp/src.txt
cmp /tmp/src.txt docs/requirements/lawdesk-comprehensive-source.txt && echo IDENTICAL
```
