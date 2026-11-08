import sys, zipfile, re
import xml.etree.ElementTree as ET
W='{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
z=zipfile.ZipFile(sys.argv[1])
root=ET.fromstring(z.read('word/document.xml'))
body=root.find(W+'body')
def ptext(p):
    out=[]
    for n in p.iter():
        if n.tag==W+'t': out.append(n.text or '')
        elif n.tag==W+'tab': out.append('\t')
        elif n.tag==W+'br': out.append('\n')
    return ''.join(out)
def pinfo(p):
    ppr=p.find(W+'pPr'); style=''; lvl=None
    if ppr is not None:
        s=ppr.find(W+'pStyle')
        if s is not None: style=s.get(W+'val')
        num=ppr.find(W+'numPr')
        if num is not None:
            il=num.find(W+'ilvl'); lvl=int(il.get(W+'val')) if il is not None else 0
    return style,lvl
lines=[]
for el in body:
    if el.tag==W+'p':
        t=ptext(el).strip()
        if not t: continue
        st,lvl=pinfo(el)
        if st.lower().startswith('heading') or st=='Title':
            n=re.sub(r'\D','',st) or '1'
            lines.append('\n'+'#'*int(n)+' '+t)
        elif lvl is not None:
            lines.append('  '*lvl+'- '+t)
        else:
            lines.append(t)
    elif el.tag==W+'tbl':
        lines.append('')
        for tr in el.iter(W+'tr'):
            cells=[' '.join(ptext(p).strip() for p in tc.iter(W+'p') if ptext(p).strip()) for tc in tr.findall(W+'tc')]
            lines.append('| '+' | '.join(cells)+' |')
        lines.append('')
print('\n'.join(lines))
