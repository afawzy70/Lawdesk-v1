(function(){
'use strict';
var d=document,root=d.documentElement;
function safe(f){try{return f()}catch(e){return null}}
var saved=safe(function(){return localStorage.getItem('theme')});if(saved)root.setAttribute('data-theme',saved);
var t=d.getElementById('theme');if(t)t.addEventListener('click',function(){var cur=root.getAttribute('data-theme')||(matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light');var n=cur==='dark'?'light':'dark';root.setAttribute('data-theme',n);safe(function(){localStorage.setItem('theme',n)})});
var mb=d.getElementById('menu'),sd=d.getElementById('side');if(mb&&sd)mb.addEventListener('click',function(){var o=sd.classList.toggle('open');mb.setAttribute('aria-expanded',o)});
// Arabic-aware search
function norm(s){return (s||'').toLowerCase().replace(/[ً-ْـ]/g,'').replace(/[إأآٱ]/g,'ا').replace(/ى/g,'ي').replace(/ة/g,'ه').replace(/[ی]/g,'ي').replace(/ک/g,'ك')}
var q=d.getElementById('q'),box=d.getElementById('results'),idx=null,base=document.body.getAttribute('data-base')||'';
function load(cb){if(idx)return cb();fetch(base+'search-index.json').then(function(r){return r.json()}).then(function(j){idx=j.map(function(e){e._n=norm(e.title+' '+e.text);return e});cb()}).catch(function(){idx=[];cb()})}
function run(){var v=norm(q.value.trim());if(!v){box.hidden=true;return}var terms=v.split(/\s+/);load(function(){var hits=idx.filter(function(e){return terms.every(function(w){return e._n.indexOf(w)>-1})}).slice(0,12);box.innerHTML=hits.length?hits.map(function(e){return '<a href="'+base+e.url+'"><b>'+esc(e.title)+'</b><br><small>'+esc(e.cat)+'</small></a>'}).join(''):'<p class="none">لا توجد نتائج مطابقة.</p>';box.hidden=false})}
function esc(s){return String(s).replace(/[&<>"]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]})}
if(q){q.addEventListener('input',run);q.addEventListener('keydown',function(e){if(e.key==='Escape'){box.hidden=true}if(e.key==='ArrowDown'){var a=box.querySelector('a');if(a){a.focus();e.preventDefault()}}});d.addEventListener('click',function(e){if(!box.contains(e.target)&&e.target!==q)box.hidden=true});box.addEventListener('keydown',function(e){var a=d.activeElement;if(e.key==='ArrowDown'&&a.nextElementSibling){a.nextElementSibling.focus();e.preventDefault()}if(e.key==='ArrowUp'&&a.previousElementSibling){a.previousElementSibling.focus();e.preventDefault()}});d.addEventListener('keydown',function(e){if(e.key==='/'&&!/INPUT|TEXTAREA/.test((d.activeElement||{}).tagName)){q.focus();e.preventDefault()}})}
// walkthrough
var st=d.querySelector('.stepper');if(st){var steps=[].slice.call(st.querySelectorAll('.step')),i=0,bar=st.querySelector('.bar i'),cnt=st.querySelector('.count'),prev=st.querySelector('[data-prev]'),next=st.querySelector('[data-next]');
function show(n){i=Math.max(0,Math.min(steps.length-1,n));steps.forEach(function(s,k){s.classList.toggle('on',k===i)});bar.style.inlineSize=((i+1)/steps.length*100)+'%';st.querySelector('[role=progressbar]').setAttribute('aria-valuenow',i+1);cnt.textContent='الخطوة '+(i+1)+' من '+steps.length;prev.disabled=i===0;next.disabled=i===steps.length-1;safe(function(){history.replaceState(null,'','#step-'+(i+1))})}
prev.addEventListener('click',function(){show(i-1)});next.addEventListener('click',function(){show(i+1)});
st.addEventListener('keydown',function(e){if(/INPUT|TEXTAREA/.test(e.target.tagName))return;if(e.key==='ArrowLeft'){show(i+1);e.preventDefault()}if(e.key==='ArrowRight'){show(i-1);e.preventDefault()}});
var m=/^#step-(\d+)$/.exec(location.hash);show(m?parseInt(m[1],10)-1:0);
st.querySelectorAll('[data-goto]').forEach(function(b){b.addEventListener('click',function(){show(parseInt(b.getAttribute('data-goto'),10))})})}
})();
