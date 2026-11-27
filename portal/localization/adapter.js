/* Arabic localization adapter for the unmodified Floorplan viewer (injected at build time by tools/localize_map.py).
 * It rewrites visible English UI text, tooltips, aria-labels and placeholders through ar.json and keeps code, paths and
 * identifiers left-to-right. Strings with no entry are left as they are, and `window.__FP_UNTRANSLATED` lists them so the
 * browser test can fail on visible English. Never touches graph data. */
(function () {
  'use strict';
  var D = /*DICT*/ {};
  var exact = D.exact, pats = D.patterns.map(function (p) { return [new RegExp(p[0]), p[1]]; });
  var ARABIC = /[؀-ۿ]/, LATIN = /[A-Za-z]/;
  var untranslated = (window.__FP_UNTRANSLATED = {});
  var MONTHS = { Jan: 'يناير', Feb: 'فبراير', Mar: 'مارس', Apr: 'أبريل', May: 'مايو', Jun: 'يونيو', Jul: 'يوليو', Aug: 'أغسطس', Sep: 'سبتمبر', Oct: 'أكتوبر', Nov: 'نوفمبر', Dec: 'ديسمبر' };
  function date(t) { var m = /^([A-Z][a-z]{2})[a-z]* (\d{1,2}), (\d{4})$/.exec(t); return m && MONTHS[m[1]] ? m[2] + ' ' + MONTHS[m[1]] + ' ' + m[3] : null; }
  function lookup(raw) {
    var t = raw.replace(/\s+/g, ' ').trim();
    if (!t) return null;
    if (Object.prototype.hasOwnProperty.call(exact, t)) return exact[t];
    for (var i = 0; i < pats.length; i++) {
      var m = pats[i][0].exec(t);
      if (m && pats[i][1] === '@DATE') return date(t);
      if (m && pats[i][1] === '@CONN') return exact[m[1]] + ' · ' + m[2];
      if (m && pats[i][1] === '@CONN2') return exact[m[1]] + ' · ' + m[2] + ' اتصال' + (m[3] ? ' · ' + m[3] + ' نقطة نهاية' : '');
      if (m && pats[i][1] === '@KIND') return (exact[m[1]] || m[1]) + ' · ' + m[2] + ' ملف' + (m[3] ? ' · ' + m[3] + ' سطر' : '');
      if (m && pats[i][1] === '@KIND2') return (exact[m[1]] || m[1]) + ' · ' + m[2] + ' سطر';
      if (m) return pats[i][1].replace(/\{(\d)\}/g, function (_, k) { return date(m[k]) || m[k]; });
    }
    return null;
  }
  function codeLike(t) { return /^[a-z][a-z0-9]*(-[a-z0-9]+)+$/.test(t) || /^[a-z+#]+ \d+%$/i.test(t) || /^[A-Z]$/.test(t) || /[\/\\._:@#*{}()\[\]<>=]|[a-z][A-Z]|^\d[\d.,k%·\s]*$|^[A-Z0-9_ -]{2,}$/.test(t) || !/\s/.test(t) && /\d/.test(t); }
  function ltr(el) { if (el && el.style) { el.style.direction = 'ltr'; el.style.unicodeBidi = 'isolate'; } }
  function text(n) {
    var raw = n.nodeValue; if (!raw || !LATIN.test(raw)) return;
    var p = n.parentElement; if (!p || /^(SCRIPT|STYLE|CODE|PRE|SVG|TEXTAREA)$/i.test(p.tagName)) return;
    var r = lookup(raw);
    if (r !== null) { n.nodeValue = raw.replace(raw.trim(), r); return; }
    var t = raw.replace(/\s+/g, ' ').trim();
    if (!ARABIC.test(t)) { if (codeLike(t)) ltr(p); else untranslated[t] = 1; }
  }
  var ATTRS = ['title', 'aria-label', 'placeholder'];
  function attrs(e) {
    ATTRS.forEach(function (a) {
      var v = e.getAttribute && e.getAttribute(a); if (!v || !LATIN.test(v) || ARABIC.test(v)) return;
      var r = lookup(v); if (r !== null) e.setAttribute(a, r); else if (!codeLike(v)) untranslated['@' + a + ':' + v] = 1;
    });
  }
  var busy = false;
  function walk(root) {
    if (busy) return; busy = true;
    try {
      if (root.nodeType === 3) text(root);
      else if (root.nodeType === 1) {
        attrs(root);
        var w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT); while (w.nextNode()) text(w.currentNode);
        root.querySelectorAll && root.querySelectorAll('[title],[aria-label],[placeholder]').forEach(attrs);
      }
    } finally { busy = false; }
  }
  function start() {
    document.documentElement.lang = 'ar'; document.documentElement.dir = 'rtl';
    walk(document.body);
    new MutationObserver(function (muts) {
      muts.forEach(function (m) {
        if (m.type === 'childList') m.addedNodes.forEach(walk);
        else if (m.type === 'characterData') walk(m.target);
        else if (m.type === 'attributes') attrs(m.target);
      });
    }).observe(document.body, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ATTRS });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();
})();
