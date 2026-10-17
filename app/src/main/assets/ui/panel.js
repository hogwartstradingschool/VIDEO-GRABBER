// Shared Liquid Glass + Puppy UI. window.VGPanel works in extension content scripts, popup and the app window.
(() => {
const CH = { 2160: '#ff4d8d', 1440: '#ff9a3c', 1080: '#7c5cff', 720: '#1fb5e8', 480: '#2ecb8f', 360: '#9aa0c8' };
const colOf = h => h >= 2160 ? CH[2160] : h >= 1440 ? CH[1440] : h >= 1080 ? CH[1080] : h >= 720 ? CH[720] : h >= 480 ? CH[480] : CH[360];
const T = {
  dark:  '--tx:#fff;--mu:#d2d4f4;--gl:rgba(40,30,86,.5);--bd:rgba(255,255,255,.38);--row:rgba(255,255,255,.12);--trk:rgba(0,0,0,.3)',
  light: '--tx:#1b1638;--mu:#6d7099;--gl:rgba(255,255,255,.58);--bd:rgba(255,255,255,.95);--row:rgba(255,255,255,.72);--trk:rgba(124,92,255,.16)'
};
const PUPPY = '<svg viewBox="0 0 40 40" aria-hidden="true"><ellipse cx="8.5" cy="17" rx="6" ry="11" fill="#b9774a" transform="rotate(14 8.5 17)"/><ellipse cx="31.5" cy="17" rx="6" ry="11" fill="#b9774a" transform="rotate(-14 31.5 17)"/><circle cx="20" cy="21.5" r="12.5" fill="#fff1de"/><circle cx="14.8" cy="19.5" r="2.3" fill="#2a1f3d"/><circle cx="25.2" cy="19.5" r="2.3" fill="#2a1f3d"/><circle cx="15.5" cy="18.7" r=".85" fill="#fff"/><circle cx="25.9" cy="18.7" r=".85" fill="#fff"/><circle cx="11" cy="25" r="2.4" fill="#ff8fb5" opacity=".6"/><circle cx="29" cy="25" r="2.4" fill="#ff8fb5" opacity=".6"/><ellipse cx="20" cy="24.2" rx="3.1" ry="2.2" fill="#3b2a3a"/><path d="M17.4 28q2.6 2.2 5.2 0" fill="none" stroke="#3b2a3a" stroke-width="1.3" stroke-linecap="round"/><path d="M18.3 28.6q1.7 5 3.4 0z" fill="#ff6b9a"/></svg>';
const CSS = `
:host{display:block;font-family:Manrope,Nunito,"Segoe UI",system-ui,sans-serif}*{box-sizing:border-box;font-family:Manrope,Nunito,"Segoe UI",system-ui,sans-serif}button{font-family:inherit}
@keyframes pop{from{opacity:0;transform:translateY(10px) scale(.88)}}
@keyframes wig{20%{transform:rotate(-12deg) scale(1.1)}45%{transform:rotate(10deg) scale(1.1)}70%{transform:rotate(-6deg) scale(1.1)}}
@keyframes sh{from{background-position:-100% 0}to{background-position:220% 0}}
@keyframes bob{50%{transform:translateY(-5px) rotate(-5deg)}}
.p{position:relative;background:linear-gradient(160deg,rgba(255,255,255,.2),transparent 42%),var(--gl);backdrop-filter:blur(28px) saturate(200%);-webkit-backdrop-filter:blur(28px) saturate(200%);border:2px solid var(--bd);
box-shadow:inset 0 3px 0 rgba(255,255,255,.7),inset 0 -8px 16px rgba(255,255,255,.07),0 26px 60px rgba(60,20,120,.4),0 0 0 6px rgba(255,255,255,.05);padding:14px;border-radius:40px;display:flex;flex-direction:column;gap:10px}
.r{--c:#7c5cff;position:relative;overflow:hidden;display:flex;align-items:center;gap:8px;padding:12px 12px 12px 18px;border-radius:32px;
background:radial-gradient(120% 150% at 0% 0%,color-mix(in srgb,var(--c) 36%,transparent),transparent 62%),var(--row);border:2px solid var(--bd);
box-shadow:inset 0 3px 0 rgba(255,255,255,.55),inset 0 -5px 10px rgba(255,255,255,.07),0 6px 14px rgba(40,10,90,.18);transition:transform .25s cubic-bezier(.34,1.7,.64,1)}
.r:hover{transform:scale(1.025) rotate(-.4deg)}.r.act{padding-bottom:26px}
.q{flex:1;min-width:0;color:var(--tx);font-weight:800;font-size:16px;white-space:nowrap}
.c{color:#fff;font-size:11px;font-weight:800;border-radius:999px;padding:3px 9px;margin-left:6px;vertical-align:middle;background:var(--c);box-shadow:inset 0 2px 0 rgba(255,255,255,.5),inset 0 -2px 3px rgba(0,0,0,.18),0 3px 8px color-mix(in srgb,var(--c) 55%,transparent)}
.s{color:var(--mu);font-size:13px;font-weight:700;white-space:nowrap}.m{display:flex;flex-direction:column;align-items:flex-end;gap:1px}.m .s{font-size:12.5px;color:var(--tx)}.s2{color:var(--mu);font-size:11px;font-weight:700;white-space:nowrap}
.b{width:46px;height:46px;flex:none;border-radius:50%;border:2.5px solid rgba(255,255,255,.92);background:radial-gradient(circle at 30% 22%,rgba(255,255,255,.75),transparent 38%),linear-gradient(135deg,#ff5c98,#8a64ff);
box-shadow:inset 0 -5px 8px rgba(80,20,140,.35),0 8px 18px rgba(255,77,141,.5);display:flex;align-items:center;justify-content:center;cursor:pointer;padding:0;transition:transform .25s cubic-bezier(.34,1.8,.64,1)}
.b:hover{animation:wig .5s ease}.b:active{transform:scale(.86,1.1)}
.b.x{background:var(--row);border-color:var(--bd);box-shadow:inset 0 2px 0 rgba(255,255,255,.4);}.b.x svg{stroke:var(--tx)}.b.sm{width:32px;height:32px}.b.ok{background:radial-gradient(circle at 30% 22%,rgba(255,255,255,.75),transparent 38%),linear-gradient(135deg,#2ecb8f,#1fb5e8);box-shadow:inset 0 -5px 8px rgba(0,80,90,.3),0 8px 18px rgba(46,203,143,.45)}
.trk{position:absolute;left:18px;right:18px;bottom:8px;height:9px;border-radius:99px;background:var(--trk);overflow:hidden;box-shadow:inset 0 2px 3px rgba(0,0,0,.2)}
.bar{height:100%;border-radius:99px;background:linear-gradient(90deg,#ff4d8d,#ffa24c);box-shadow:inset 0 2px 0 rgba(255,255,255,.55);position:relative;transition:width .4s}
.bar::after{content:"";position:absolute;inset:0;background:linear-gradient(90deg,transparent,rgba(255,255,255,.6),transparent);background-size:55% 100%;background-repeat:no-repeat;animation:sh 1.2s linear infinite}
.pz .bar{opacity:.55}.pz .bar::after{display:none}
.e{color:#ff6b8a;font-size:12px;font-weight:800;max-width:170px;line-height:1.25}
.empty{color:var(--mu);font-weight:800;padding:14px 10px 10px;text-align:center;font-size:14px}.pup{width:64px;height:64px;margin:0 auto 6px;animation:bob 1.6s ease-in-out infinite}.pup svg{width:100%;height:100%}
/* job cards */
.j{--c:#7c5cff;position:relative;border-radius:32px;padding:14px 16px 16px;margin-bottom:12px;background:radial-gradient(120% 120% at 0% 0%,color-mix(in srgb,var(--c) 30%,transparent),transparent 60%),var(--gl);
backdrop-filter:blur(26px) saturate(190%);-webkit-backdrop-filter:blur(26px) saturate(190%);border:2px solid var(--bd);box-shadow:inset 0 3px 0 rgba(255,255,255,.6),0 10px 26px rgba(40,10,90,.25)}
.anim .r,:host(.anim) .r{animation:pop .55s cubic-bezier(.34,1.7,.64,1) both;animation-delay:calc(var(--i)*55ms)}.anim .j,:host(.anim) .j{animation:pop .5s cubic-bezier(.34,1.7,.64,1) both}
.jt{display:flex;align-items:center;gap:10px}.jt b{flex:1;min-width:0;font-weight:800;font-size:14px;color:var(--tx);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.sub{color:var(--mu);font-size:12px;font-weight:700;margin:6px 2px 0}
.jtr{position:relative;height:11px;border-radius:99px;background:var(--trk);overflow:hidden;margin-top:10px;box-shadow:inset 0 2px 3px rgba(0,0,0,.2)}
.st{display:grid;grid-template-columns:repeat(4,1fr);gap:7px;margin-top:10px}
.st div{background:var(--row);border:1.5px solid var(--bd);border-radius:18px;padding:6px 4px 7px;text-align:center;box-shadow:inset 0 2px 0 rgba(255,255,255,.4)}
.st i{display:block;font-style:normal;font-size:9.5px;color:var(--mu);font-weight:800;text-transform:uppercase;letter-spacing:.05em}.st u{text-decoration:none;font-size:12.5px;font-weight:800;color:var(--tx);white-space:nowrap}
.jb{display:flex;gap:8px;margin-top:12px;justify-content:flex-end}
.pb{border:2px solid rgba(255,255,255,.92);border-radius:999px;padding:8px 18px;font:800 12.5px Manrope,"Segoe UI",system-ui,sans-serif;color:#fff;cursor:pointer;background:radial-gradient(circle at 30% 10%,rgba(255,255,255,.55),transparent 45%),linear-gradient(135deg,#ff5c98,#8a64ff);
box-shadow:inset 0 -4px 6px rgba(80,20,140,.3),0 6px 14px rgba(255,77,141,.4);transition:transform .25s cubic-bezier(.34,1.8,.64,1)}.pb:hover{transform:scale(1.08) rotate(-2deg)}.pb:active{transform:scale(.92,1.08)}

.q,.m,.b{position:relative;z-index:2}
.wf{position:absolute;left:0;top:0;bottom:0;background:linear-gradient(90deg,rgba(255,77,141,.5),rgba(255,162,76,.6));transition:width .5s;pointer-events:none;z-index:0}
.wv{position:absolute;right:-13px;top:0;bottom:0;width:14px;background:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='40'%3E%3Cpath d='M0 0H4Q14 10 4 20T4 40H0Z' fill='%23ffa24c' fill-opacity='.6'/%3E%3C/svg%3E") repeat-y;background-size:14px 40px;animation:wave 1.1s linear infinite}
@keyframes wave{to{background-position:0 40px}}@keyframes rise{to{transform:translateY(-70px);opacity:0}}@keyframes swim{50%{transform:translateY(-3px) rotate(-6deg)}}
.bu{position:absolute;bottom:-6px;width:7px;height:7px;border-radius:50%;background:rgba(255,255,255,.6);animation:rise 2.4s ease-in infinite}.b1{left:18%}.b2{left:50%;animation-delay:.8s;width:5px;height:5px}.b3{left:78%;animation-delay:1.5s}
.pz .wf{opacity:.55}.pz .wv,.pz .swim{animation:none}.pz .bu{display:none}
.swim{position:absolute;bottom:5px;width:26px;height:26px;z-index:1;pointer-events:none;transition:left .5s;animation:swim 1.4s ease-in-out infinite}.swim svg{width:100%;height:100%;display:block}
.swim::after{content:"";position:absolute;left:-3px;right:-3px;bottom:-2px;height:12px;border-radius:50%;border:3.5px solid #fff;border-left-color:#ff4d8d;border-right-color:#ff4d8d;box-shadow:0 2px 4px rgba(0,0,0,.25)}
.b.pr{position:relative;background:conic-gradient(#ffe066 calc(var(--p)*1%),rgba(255,255,255,.3) 0)}
.b.pr::before{content:"";position:absolute;inset:4px;border-radius:50%;background:radial-gradient(circle at 30% 22%,rgba(255,255,255,.6),transparent 40%),linear-gradient(135deg,#ff5c98,#8a64ff);box-shadow:inset 0 -4px 6px rgba(80,20,140,.3)}
.b.pr .pc,.b.pr .pi{position:relative;z-index:1;color:#fff}.pc{font:800 11px Manrope,"Segoe UI",sans-serif}.b.pr .pi{display:none}.b.pr:hover .pc{display:none}.b.pr:hover .pi{display:flex}
.dt{background:var(--row);border:2px solid var(--bd);border-radius:32px;padding:12px 14px 14px;box-shadow:inset 0 3px 0 rgba(255,255,255,.5);display:flex;flex-direction:column;gap:10px}
.dh{display:flex;align-items:center;gap:10px}.dh span{flex:1;min-width:0;font-size:12.5px;font-weight:800;color:var(--tx);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.pb.sm{padding:5px 12px;font-size:11.5px}
.sp{background:var(--trk);border-radius:22px;padding:8px 12px 4px;box-shadow:inset 0 2px 4px rgba(0,0,0,.12)}.spl{display:flex;justify-content:space-between;font-size:10.5px;font-weight:800;color:var(--mu)}.sp svg{width:100%;height:62px;display:block}
.st.s6{grid-template-columns:repeat(3,1fr);margin-top:0}.dt .jb{margin-top:0}.ok2{color:#2ecb8f;font-weight:800;font-size:14px;text-align:center}
.pb.g{background:var(--row);color:var(--tx);border-color:var(--bd);box-shadow:inset 0 2px 0 rgba(255,255,255,.4)}`;
const sv = (d, w = 2.6, extra = '') => `<svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round" ${extra}>${d}</svg>`;
const ICON = sv('<path d="M12 4v11"/><path d="M7 11l5 5 5-5"/><path d="M5 20h14"/>');
const OK = sv('<path d="M5 12l5 5 9-10"/>', 3);
const X = sv('<path d="M6 6l12 12M18 6L6 18"/>', 3).replace('width="19" height="19"', 'width="15" height="15"');
const PAUSE = sv('<path d="M8 5v14M16 5v14"/>', 3.6);
const PLAY = '<svg width="18" height="18" viewBox="0 0 24 24" fill="#fff"><path d="M8 4.5l12 7.5-12 7.5z"/></svg>';
const fT = s => { s = Math.max(0, Math.round(s || 0)); const h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), x = s % 60; return h ? h + ':' + String(m).padStart(2, '0') + ':' + String(x).padStart(2, '0') : m + ':' + String(x).padStart(2, '0'); };
const sz = b => b >= 1e9 ? (b / 1e9).toFixed(2) + ' GB' : b >= 1e6 ? (b / 1e6).toFixed(b >= 1e8 ? 0 : 1) + ' MB' : Math.round((b || 0) / 1e3) + ' KB';
const mb = b => b >= 1e9 ? (b / 1e9).toFixed(2) + ' GB' : Math.round(b / 1e6) + ' MB';
const esc = s => String(s == null ? '' : s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const mid3 = (a, b, c) => `<div class="m"><div class="s">${a}</div>${b ? `<div class="s2">${b}</div>` : ''}${c ? `<div class="s2">${c}</div>` : ''}</div>`;
const empty = (msg) => `<div class="empty"><div class="pup">${PUPPY}</div>${esc(msg)}</div>`;

const mbs = v => (v / 1e6).toFixed(1) + ' MB/s';
function spark(h) {
  h = (h || []).slice(-60); if (h.length < 2) h = [0, 0, ...h];
  const W = 300, H = 62, mx = Math.max(1, ...h) * 1.15, st = W / 59, pts = h.map((v, i) => [(W - (h.length - 1 - i) * st).toFixed(1), (H - 3 - v / mx * (H - 8)).toFixed(1)]);
  const line = pts.map(q => q.join(',')).join(' '), area = pts[0][0] + ',' + H + ' ' + line + ' ' + pts[pts.length - 1][0] + ',' + H;
  return `<div class="sp"><div class="spl"><span>Speed · per second</span><span>Peak ${mbs(Math.max(0, ...h))}</span></div><svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none"><defs><linearGradient id="sg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ff4d8d" stop-opacity=".55"/><stop offset="1" stop-color="#ffa24c" stop-opacity=".05"/></linearGradient></defs><polygon points="${area}" fill="url(#sg)"/><polyline points="${line}" fill="none" stroke="#ff4d8d" stroke-width="2.4" stroke-linejoin="round" stroke-linecap="round" vector-effect="non-scaling-stroke"/></svg></div>`;
}
const water = pct => { const t = Date.now(), d = (m, o = 0) => `animation-delay:-${(t + o) % m}ms`; return `<div class="wf" style="width:${pct}%"><i class="wv" style="${d(1100)}"></i><b class="bu b1" style="${d(2400)}"></b><b class="bu b2" style="${d(2400, 800)}"></b><b class="bu b3" style="${d(2400, 1500)}"></b></div><div class="swim" style="left:clamp(2px,calc(${pct}% - 15px),calc(100% - 34px));${d(1400)}">${PUPPY}</div>`; };
const ring = (j, act, icon) => `<button class="b pr" style="--p:${j.pct || 0}" data-act="${act}" data-job="${j.id}" aria-label="${act}"><span class="pc">${Math.round(j.pct || 0)}%</span><span class="pi">${icon}</span></button>`;

function rowHTML(t, j, i, focus) {
  const col = colOf(t.height || 0), lab = t.height ? t.height + 'p' : 'Best';
  let mid = `<div class="s">${t.size ? '~' + mb(t.size) : ''}</div>`, btn = `<button class="b" data-h="${t.height}" aria-label="Download ${lab}">${ICON}</button>`, bg = '', cls = '';
  const one = (a) => focus ? '' : `<div class="m"><div class="s">${a}</div></div>`;
  if (j && j.status === 'running') {
    mid = one(j.merging ? 'Merging…' : mbs(j.speed));
    btn = `<button class="b x sm" data-act="cancel" data-job="${j.id}" aria-label="Cancel">${X}</button>` + ring(j, 'pause', PAUSE); bg = water(j.pct || 0); cls = 'act';
  } else if (j && j.status === 'paused') {
    mid = one('Paused');
    btn = `<button class="b x sm" data-act="cancel" data-job="${j.id}" aria-label="Cancel">${X}</button>` + ring(j, 'resume', PLAY); bg = water(j.pct || 0); cls = 'act pz';
  } else if (j && j.status === 'done') { mid = one('Saved ✓'); btn = `<button class="b ok" data-h="${t.height}" aria-label="Download again">${OK}</button>`; }
  else if (j && j.status === 'error') { mid = `<div class="e">${esc(j.err || 'Error')}</div>`; btn = `<button class="b" data-act="resume" data-job="${j.id}" aria-label="Retry">${PLAY}</button>`; }
  return `<div class="r ${cls}" style="--c:${col};--i:${i}">${bg}<div class="q">${lab}<span class="c">${esc(t.chip)}</span></div>${mid}${btn}</div>`;
}
function detailHTML(j) {
  const run = j.status === 'running', pz = j.status === 'paused', done = j.status === 'done';
  const cell = (k, v) => `<div><i>${k}</i><u>${v}</u></div>`;
  const stats = done ? cell('Size', sz(j.total)) + cell('Took', fT(j.elapsed)) + cell('Avg speed', j.elapsed ? mbs(j.total / j.elapsed) : '—')
    : cell('Speed', run && !j.merging ? mbs(j.speed) : '—') + cell('Downloaded', sz(j.dl)) + cell('Total', j.total ? sz(j.total) : '—') + cell('Time left', run && !j.merging && j.eta ? fT(j.eta) : '—') + cell('Elapsed', fT(j.elapsed)) + cell('Done', Math.round(j.pct || 0) + '%');
  const btns = run ? [['pause', 'Pause', 1], ['cancel', 'Cancel', 0]] : pz ? [['resume', 'Resume', 1], ['cancel', 'Cancel', 0]] : done ? [['open', '📂 Open folder', 1]] : [['resume', 'Retry', 1]];
  const name = done && j.file ? j.file.split(/[\\/]/).pop() : (j.merging ? 'Merging video + audio…' : run ? 'Downloading…' : pz ? 'Paused' : j.status === 'error' ? 'Error: ' + j.err : 'Canceled');
  return `<div class="dt"><div class="dh"><button class="pb g sm" data-back="1">‹ Back</button><span title="${esc(name)}">${esc(name)}</span></div>${done ? '<div class="ok2">Saved ✓ — your video is ready!</div>' : spark(j.hist)}<div class="st s6" style="${done ? 'grid-template-columns:repeat(3,1fr)' : ''}">${stats}</div><div class="jb">${btns.map(x => `<button class="pb ${x[2] ? '' : 'g'}" data-act="${x[0]}" data-job="${j.id}">${x[1]}</button>`).join('')}</div></div>`;
}
function panelHTML(d, J, theme, emptyMsg, focus) {
  const th = T[theme] || T.dark; J = J || {};
  const fj = d && focus != null && J[focus];
  let rows;
  if (fj) { const t = d.tiers.find(x => x.height === focus); rows = t ? rowHTML(t, fj, 0, 1) + detailHTML(fj) : ''; }
  else rows = !d ? '' : d.tiers.map((t, i) => rowHTML(t, J[t.height], i, 0)).join('');
  return `<style>:host{${th}}${CSS}</style><div class="p">${rows || empty(emptyMsg || 'Qualities will appear here')}</div>`;
}

function jobsHTML(L, theme, emptyMsg) {
  const th = T[theme] || T.dark;
  const items = [...(L || [])].sort((a, b) => b.id - a.id).slice(0, 20).map(j => {
    const col = colOf(j.height || 0), lab = j.height ? j.height + 'p' : 'Best', run = j.status === 'running', pz = j.status === 'paused', done = j.status === 'done';
    const sizeTxt = j.total ? sz(j.dl) + ' of ' + sz(j.total) : (j.dl ? sz(j.dl) : '');
    let sub = '', stats = '', bar = '', btns = [];
    if (run) { sub = j.merging ? 'Merging video + audio…' : 'Downloading' + (sizeTxt ? ' · ' + sizeTxt : ''); btns = [['pause', 'Pause', 1], ['cancel', 'Cancel', 0]]; }
    else if (pz) { sub = 'Paused' + (sizeTxt ? ' · ' + sizeTxt : ''); btns = [['resume', 'Resume', 1], ['cancel', 'Cancel', 0]]; }
    else if (done) { sub = 'Saved ✓' + (j.total ? ' · ' + sz(j.total) : '') + ' · took ' + fT(j.elapsed); btns = [['open', '📂 Open folder', 1], ['remove', 'Remove', 0]]; }
    else if (j.status === 'error') { sub = 'Error: ' + esc(j.err); btns = [['resume', 'Retry', 1], ['remove', 'Remove', 0]]; }
    else { sub = 'Canceled'; btns = [['resume', 'Retry', 1], ['remove', 'Remove', 0]]; }
    if (run || pz) {
      bar = `<div class="jtr ${pz ? 'pz' : ''}"><div class="bar" style="width:${j.pct || 0}%"></div></div>` + spark(j.hist);
      stats = `<div class="st"><div><i>Speed</i><u>${run && !j.merging ? mbs(j.speed) : '—'}</u></div><div><i>Done</i><u>${Math.round(j.pct || 0)}%</u></div><div><i>Time left</i><u>${run && !j.merging && j.eta ? fT(j.eta) : '—'}</u></div><div><i>Elapsed</i><u>${fT(j.elapsed)}</u></div></div>`;
    } else if (done) bar = `<div class="jtr"><div class="bar" style="width:100%;background:linear-gradient(90deg,#2ecb8f,#1fb5e8)"></div></div>`;
    return `<div class="j" style="--c:${col}"><div class="jt"><b title="${esc(j.title || j.url)}">${esc(j.title || j.url)}</b><span class="c" style="margin:0">${lab}</span></div><div class="sub">${sub}</div>${bar}${stats}<div class="jb">${btns.map(x => `<button class="pb ${x[2] ? '' : 'g'}" data-act="${x[0]}" data-job="${j.id}">${x[1]}</button>`).join('')}</div></div>`;
  }).join('');
  return `<style>:host{${th}}${CSS}</style>${items || empty(emptyMsg || 'No downloads yet. Paste a link or tap the Download button on any video!')}`;
}
window.VGPanel = { panelHTML, jobsHTML, PUPPY };
if (window.customElements && !customElements.get('vg-panel')) {
class VgPanel extends HTMLElement {
  constructor() { super(); this.attachShadow({ mode: 'open' }); this._d = null; this._j = {}; this.shadowRoot.addEventListener('click', e => {
    const b = e.target.closest('button'); if (!b) return;
    if (b.dataset.back) { this._f = null; return this.render(); }
    if (b.dataset.act) return this.dispatchEvent(new CustomEvent('vg-job', { detail: { act: b.dataset.act, id: +b.dataset.job } }));
    const t = this._d.tiers.find(x => x.height === +b.dataset.h); this._f = t.height; this.dispatchEvent(new CustomEvent('vg-download', { detail: t }));
  }); }
  static get observedAttributes() { return ['theme']; }
  attributeChangedCallback() { this.render(); }
  set data(d) { this._d = d; this._f = null; this.render(); }
  set jobs(j) { this._j = j || {}; this.render(); }
  render() { if (!this._a && this._d) { this._a = 1; this.classList.add('anim'); setTimeout(() => this.classList.remove('anim'), 900); } this.shadowRoot.innerHTML = panelHTML(this._d, this._j, this.getAttribute('theme'), '', this._f); }
}
customElements.define('vg-panel', VgPanel);
class VgJobs extends HTMLElement {
  constructor() { super(); this.attachShadow({ mode: 'open' }); this._l = []; this._k = ''; this.shadowRoot.addEventListener('click', e => {
    const b = e.target.closest('button'); if (b && b.dataset.act) this.dispatchEvent(new CustomEvent('vg-job', { detail: { act: b.dataset.act, id: +b.dataset.job } }));
  }); }
  static get observedAttributes() { return ['theme']; }
  attributeChangedCallback() { this._k = ''; this.render(); }
  set data(l) { this._l = l || []; this.render(); }
  render() { if (!this._a && this._l.length) { this._a = 1; this.classList.add('anim'); setTimeout(() => this.classList.remove('anim'), 900); } const h = jobsHTML(this._l, this.getAttribute('theme')); if (h !== this._k) { this._k = h; this.shadowRoot.innerHTML = h; } }
}
customElements.define('vg-jobs', VgJobs);
}
})();
