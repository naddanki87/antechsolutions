'use strict';

// Antech Mission Control - renders the /ship agents' live event stream. All text goes through textContent.

const NS = 'http://www.w3.org/2000/svg';
const CX = 500, CY = 400, RX = 380, RY = 280, NODE_R = 52, CEO_R = 96;

const ICONS = {
  clipboard: [['path', { d: 'M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2' }], ['rect', { x: 8, y: 2, width: 8, height: 4, rx: 1 }], ['path', { d: 'M8 12h8M8 16h5' }]],
  phone: [['rect', { x: 5, y: 2, width: 14, height: 20, rx: 2 }], ['path', { d: 'M12 18h.01M9 5h6' }]],
  layout: [['rect', { x: 3, y: 3, width: 18, height: 18, rx: 2 }], ['path', { d: 'M3 9h18M9 21V9' }]],
  file: [['path', { d: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z' }], ['path', { d: 'M14 2v6h6M16 13H8M16 17H8M10 9H8' }]],
  shield: [['path', { d: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z' }], ['path', { d: 'M9 12l2 2 4-4' }]],
  lock: [['rect', { x: 3, y: 11, width: 18, height: 11, rx: 2 }], ['path', { d: 'M7 11V7a5 5 0 0 1 10 0v4M12 15v3' }]],
  branch: [['path', { d: 'M6 3v12' }], ['circle', { cx: 18, cy: 6, r: 3 }], ['circle', { cx: 6, cy: 18, r: 3 }], ['path', { d: 'M18 9a9 9 0 0 1-9 9' }]],
  cpu: [['rect', { x: 4, y: 4, width: 16, height: 16, rx: 2 }], ['rect', { x: 9, y: 9, width: 6, height: 6 }], ['path', { d: 'M9 1v3M15 1v3M9 20v3M15 20v3M20 9h3M20 14h3M1 9h3M1 14h3' }]],
};

// Ring order, clockwise from the top (matches the org chart).
const AGENTS = [
  { name: 'Spec', role: 'Lead Technical Architect', color: '#38d6ff', icon: 'clipboard' },
  { name: 'Swift', role: 'Mobile App Developer', color: '#ffb347', icon: 'phone' },
  { name: 'Pixel', role: 'Minimalist Frontend', color: '#5ec8ff', icon: 'layout' },
  { name: 'Scribe', role: 'Documentation', color: '#b689ff', icon: 'file' },
  { name: 'Hawk', role: 'QA, Test & Reviewer', color: '#3ee0c8', icon: 'shield' },
  { name: 'Armor', role: 'Security Analyzer', color: '#2fc4ff', icon: 'lock' },
  { name: 'Deployer', role: 'Git & GitHub DevOps', color: '#ff9a3c', icon: 'branch' },
  { name: 'Byte', role: 'Spring Boot Backend', color: '#6aa9ff', icon: 'cpu' },
];
const PHASES = ['Toolchain', 'Architecture', 'Implementation', 'Security & QA', 'Delivery', 'Handover'];
const STATUS_COLORS = { idle: '#7f93ab', working: '#38d6ff', waiting: '#ffb347', blocked: '#ff5d6c', done: '#3ee08f' };
const CEO_NAMES = ['CEO', 'CEO Nageshwar', 'Nageshwar'];

const $ = (id) => document.getElementById(id);
const byName = Object.fromEntries(AGENTS.map((a) => [a.name, a]));
let state;
let live = false;
let focusAgent = null;
const series = { cpu: [], mem: [], rate: [] };

function svg(tag, attrs, parent) {
  const el = document.createElementNS(NS, tag);
  for (const [k, v] of Object.entries(attrs || {})) el.setAttribute(k, v);
  if (parent) parent.appendChild(el);
  return el;
}

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function freshState() {
  const agents = {};
  for (const a of AGENTS) agents[a.name] = { status: 'idle', task: 'Standing by', updated: null, history: [], sent: 0 };
  return { events: [], agents, phase: null, gates: {}, tests: null, security: null, artifacts: [] };
}

// ------------------------------------------------------------------ network drawing

AGENTS.forEach((a, i) => {
  const angle = (-90 + i * 45) * Math.PI / 180;
  a.x = CX + RX * Math.cos(angle);
  a.y = CY + RY * Math.sin(angle);
});

function pointOf(name) {
  if (CEO_NAMES.includes(name)) return { x: CX, y: CY, r: CEO_R };
  const a = byName[name];
  return a ? { x: a.x, y: a.y, r: NODE_R } : null;
}

const edges = new Map();
const edgeKey = (a, b) => [a, b].sort().join('|');

function drawNetwork() {
  const orbits = $('orbits');
  svg('ellipse', { class: 'orbit spin', cx: CX, cy: CY, rx: RX, ry: RY }, orbits);
  svg('ellipse', { class: 'orbit', cx: CX, cy: CY, rx: RX * 0.62, ry: RY * 0.62 }, orbits);
  svg('circle', { class: 'orbit spin', cx: CX, cy: CY, r: CEO_R + 34 }, orbits);

  const edgeLayer = $('edges');
  AGENTS.forEach((a, i) => {
    const spoke = svg('line', { class: 'edge', x1: CX, y1: CY, x2: a.x, y2: a.y, stroke: a.color }, edgeLayer);
    edges.set(edgeKey('CEO', a.name), spoke);
    const b = AGENTS[(i + 1) % AGENTS.length];
    const mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2;
    const k = 1.18; // bow outward
    const qx = CX + (mx - CX) * k, qy = CY + (my - CY) * k;
    const arc = svg('path', { class: 'edge', d: `M${a.x},${a.y} Q${qx},${qy} ${b.x},${b.y}`, stroke: a.color }, edgeLayer);
    edges.set(edgeKey(a.name, b.name), arc);
    if (i % 2 === 0) {
      const label = svg('text', { class: 'edge-label', x: qx, y: qy, 'text-anchor': 'middle' }, edgeLayer);
      label.textContent = 'communication';
    }
  });

  drawCeo();
  const nodeLayer = $('nodes');
  for (const a of AGENTS) drawNode(a, nodeLayer);
}

function drawCeo() {
  const g = $('ceo');
  svg('circle', { class: 'ceo-ring', cx: CX, cy: CY, r: CEO_R }, g);
  const initial = svg('text', { class: 'ceo-initial', x: CX, y: CY + 22 }, g);
  initial.textContent = 'N';
  const name = svg('text', { class: 'ceo-name', x: CX, y: CY + CEO_R + 34 }, g);
  name.textContent = 'Nageshwar';
  const role = svg('text', { class: 'ceo-role', x: CX, y: CY + CEO_R + 54 }, g);
  role.textContent = 'CEO';

  // Optional photo: drop a ceo.jpg next to index.html to show it inside the ring.
  const img = new Image();
  img.onload = () => {
    const clip = svg('clipPath', { id: 'ceo-clip' }, $('network').querySelector('defs'));
    svg('circle', { cx: CX, cy: CY, r: CEO_R - 6 }, clip);
    svg('image', { href: 'ceo.jpg', x: CX - CEO_R, y: CY - CEO_R, width: CEO_R * 2, height: CEO_R * 2,
      'clip-path': 'url(#ceo-clip)', preserveAspectRatio: 'xMidYMid slice' }, g);
    initial.remove();
  };
  img.src = 'ceo.jpg';
}

function drawNode(a, layer) {
  const g = svg('g', { class: 'node', tabindex: 0, role: 'button', 'aria-label': `${a.name}, ${a.role}` }, layer);
  svg('circle', { class: 'halo', cx: a.x, cy: a.y, r: NODE_R + 18, fill: a.color }, g);
  svg('circle', { class: 'ring', cx: a.x, cy: a.y, r: NODE_R, stroke: a.color }, g);
  const spinner = svg('circle', { class: 'spinner', cx: a.x, cy: a.y, r: NODE_R + 9, stroke: a.color }, g);
  spinner.style.transformOrigin = `${a.x}px ${a.y}px`;
  const icon = svg('g', { class: 'icon', stroke: a.color, transform: `translate(${a.x - 21} ${a.y - 21}) scale(1.75)` }, g);
  for (const [tag, attrs] of ICONS[a.icon]) svg(tag, attrs, icon);

  const top = a.y + NODE_R + 30;
  svg('text', { class: 'name', x: a.x, y: top }, g).textContent = a.name;
  svg('text', { class: 'role', x: a.x, y: top + 18 }, g).textContent = a.role;
  const statusBg = svg('rect', { class: 'status-bg', x: a.x - 42, y: top + 27, width: 84, height: 18, fill: 'rgba(127,147,171,.15)' }, g);
  const status = svg('text', { class: 'status', x: a.x, y: top + 40 }, g);
  const task1 = svg('text', { class: 'task', x: a.x, y: top + 62 }, g);
  const task2 = svg('text', { class: 'task', x: a.x, y: top + 77 }, g);
  a.view = { g, status, statusBg, task1, task2 };

  const open = () => setFocus(focusAgent === a.name ? null : a.name);
  g.addEventListener('click', open);
  g.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(); } });
}

function wrap(text, width, lines) {
  const words = String(text || '').split(/\s+/);
  const out = [''];
  for (const w of words) {
    const cur = out[out.length - 1];
    if ((cur + ' ' + w).trim().length <= width) out[out.length - 1] = (cur + ' ' + w).trim();
    else if (out.length < lines) out.push(w.slice(0, width));
    else { out[out.length - 1] = cur.slice(0, width - 1) + '…'; break; }
  }
  return out;
}

function renderNode(name) {
  const a = byName[name];
  const s = state.agents[name];
  if (!a || !a.view) return;
  a.view.g.classList.remove('idle', 'working', 'waiting', 'blocked', 'done');
  a.view.g.classList.add(s.status);
  a.view.g.classList.toggle('focus', focusAgent === name);
  const color = STATUS_COLORS[s.status] || STATUS_COLORS.idle;
  a.view.status.textContent = s.status;
  a.view.status.setAttribute('fill', color);
  a.view.statusBg.setAttribute('stroke', color);
  a.view.statusBg.setAttribute('stroke-opacity', '.5');
  const [l1, l2 = ''] = wrap(s.task, 30, 2);
  a.view.task1.textContent = l1;
  a.view.task2.textContent = l2;
}

function pulse(from, to, color) {
  const p1 = pointOf(from), p2 = pointOf(to);
  if (!p1 || !p2 || from === to) return;
  const edge = edges.get(edgeKey(CEO_NAMES.includes(from) ? 'CEO' : from, CEO_NAMES.includes(to) ? 'CEO' : to));
  let temp = null;
  if (edge) {
    edge.classList.add('hot');
    setTimeout(() => edge.classList.remove('hot'), 1400);
  } else {
    temp = svg('line', { class: 'edge hot', x1: p1.x, y1: p1.y, x2: p2.x, y2: p2.y, stroke: color }, $('edges'));
  }
  const layer = $('pulses');
  const dots = [0, 1, 2].map((i) => svg('circle', { class: 'pulse', r: 7 - i * 2, fill: color, opacity: 1 - i * 0.3 }, layer));
  // Ring arcs are curved paths: follow the curve, in the right direction. Spokes are straight lines.
  const pathEl = edge && edge.tagName === 'path' ? edge : null;
  const len = pathEl ? pathEl.getTotalLength() : 0;
  const startsAtFrom = pathEl ? Math.hypot(pathEl.getPointAtLength(0).x - p1.x, pathEl.getPointAtLength(0).y - p1.y) < 2 : true;
  const t0 = performance.now(), dur = 1100;
  function frame(now) {
    const t = Math.min(1, (now - t0) / dur);
    dots.forEach((d, i) => {
      const tt = Math.max(0, t - i * 0.05);
      let x, y;
      if (pathEl) {
        const pt = pathEl.getPointAtLength((startsAtFrom ? tt : 1 - tt) * len);
        x = pt.x; y = pt.y;
      } else {
        x = p1.x + (p2.x - p1.x) * tt; y = p1.y + (p2.y - p1.y) * tt;
      }
      d.setAttribute('cx', x);
      d.setAttribute('cy', y);
    });
    if (t < 1) requestAnimationFrame(frame);
    else {
      dots.forEach((d) => d.remove());
      if (temp) setTimeout(() => temp.remove(), 500);
    }
  }
  requestAnimationFrame(frame);
}

// ------------------------------------------------------------------ event handling

function normalizeStatus(ev) {
  if (ev.status && STATUS_COLORS[ev.status]) return ev.status;
  if (ev.kind === 'done') return 'done';
  if (ev.kind === 'gate') return /fail/i.test(ev.text) ? 'blocked' : 'done';
  return 'working';
}

function apply(ev) {
  state.events.push(ev);
  const s = state.agents[ev.agent];
  if (s) {
    s.status = normalizeStatus(ev);
    s.task = ev.text;
    s.updated = ev.ts;
    s.history.push(ev);
    if (ev.to && ev.to.length) s.sent++;
  }
  if (Number.isInteger(ev.phase)) state.phase = ev.phase;
  const d = ev.data || {};
  if (ev.kind === 'gate' && Number.isInteger(ev.phase)) state.gates[ev.phase] = /fail/i.test(ev.text) ? 'fail' : 'pass';
  if (ev.kind === 'test' && d.run !== undefined) state.tests = d;
  if (ev.kind === 'security' && (d.open !== undefined || d.fixed !== undefined)) state.security = { ...(state.security || {}), ...d };
  if (ev.kind === 'artifact' && d.name) state.artifacts = state.artifacts.filter((x) => x.name !== d.name).concat(d);

  if (live) {
    renderNode(ev.agent);
    for (const target of ev.to || []) pulse(ev.agent, target, (byName[ev.agent] || {}).color || '#38d6ff');
    if (matchesFilter(ev)) prependEntry(ev, true);
    if (focusAgent === ev.agent) renderCard();
    renderSummary();
  }
}

function matchesFilter(ev) {
  return !focusAgent || ev.agent === focusAgent || (ev.to || []).includes(focusAgent);
}

function entryNode(ev, animate) {
  const li = el('li', `entry ${ev.kind || ''}`);
  if (ev.kind === 'gate') li.classList.add(/fail/i.test(ev.text) ? 'fail' : 'pass');
  if (!animate) li.style.animation = 'none';
  li.style.setProperty('--c', (byName[ev.agent] || {}).color || '#38d6ff');
  const who = el('div', 'who', ev.agent || 'System');
  if (ev.to && ev.to.length) {
    who.append(el('span', 'arrow', ' -> '), document.createTextNode(ev.to.join(', ')));
  }
  li.append(who, el('div', 'text', ev.text || ''));
  const meta = el('div', 'meta');
  meta.append(el('span', '', ev.ts ? new Date(ev.ts).toLocaleTimeString() : ''));
  if (Number.isInteger(ev.phase)) meta.append(el('span', '', `P${ev.phase} ${PHASES[ev.phase] || ''}`));
  if (ev.kind && ev.kind !== 'message') meta.append(el('span', '', ev.kind));
  li.append(meta);
  return li;
}

function prependEntry(ev, animate) {
  const log = $('log');
  log.prepend(entryNode(ev, animate));
  while (log.children.length > 400) log.lastChild.remove();
}

function renderLog() {
  const log = $('log');
  const items = state.events.filter(matchesFilter).slice(-400).reverse().map((ev) => entryNode(ev, false));
  log.replaceChildren(...items);
  if (!items.length) log.append(el('li', 'muted small', 'Waiting for the agents to start…'));
  $('log-filter-clear').hidden = !focusAgent;
  $('log-filter-note').hidden = !focusAgent;
  $('log-filter-note').textContent = focusAgent ? `Showing ${focusAgent}'s conversation` : '';
}

function setFocus(name) {
  focusAgent = name;
  for (const a of AGENTS) renderNode(a.name);
  renderLog();
  renderCard();
}

function renderCard() {
  const card = $('agent-card');
  if (!focusAgent) { card.hidden = true; return; }
  const a = byName[focusAgent], s = state.agents[focusAgent];
  card.hidden = false;
  $('ac-swatch').style.background = a.color;
  $('ac-name').textContent = a.name;
  $('ac-role').textContent = `${a.role} · ${s.history.length} updates · ${s.sent} messages sent`;
  $('ac-status').textContent = s.status;
  $('ac-status').style.color = STATUS_COLORS[s.status];
  $('ac-task').textContent = s.task;
  const items = s.history.slice(-12).reverse().map((ev) => {
    const li = el('li');
    li.append(el('span', 'muted mono', `${new Date(ev.ts).toLocaleTimeString()}  `), document.createTextNode(ev.text));
    return li;
  });
  $('ac-log').replaceChildren(...items);
}

function renderSummary() {
  const phases = $('phases');
  phases.replaceChildren(...PHASES.map((p, i) => {
    const li = el('li', 'phase', `P${i} ${p}`);
    const gate = state.gates[i];
    if (gate) li.classList.add(gate);
    else if (state.phase === i) li.classList.add('active');
    return li;
  }));
  const t = state.tests;
  $('t-run').textContent = t ? t.run : '-';
  $('t-fail').textContent = t ? t.failed : '-';
  $('t-skip').textContent = t ? (t.skipped ?? 0) : '-';
  const sec = state.security;
  $('s-open').textContent = sec && sec.open !== undefined ? sec.open : '-';
  $('s-fixed').textContent = sec && sec.fixed !== undefined ? sec.fixed : '-';
  $('s-sign').textContent = sec && sec.signoff ? '✔' : '-';
  $('s-sign').className = sec && sec.signoff ? 'good' : '';
  const arts = $('artifacts');
  if (state.artifacts.length) {
    arts.replaceChildren(...state.artifacts.map((x) => {
      const li = el('li');
      li.append(el('span', 'mono', x.name), el('span', 'muted', x.size ? `  ${x.size}` : ''));
      return li;
    }));
  }
}

function renderAll() {
  for (const a of AGENTS) renderNode(a.name);
  renderLog();
  renderCard();
  renderSummary();
}

// ------------------------------------------------------------------ live connection

function connect() {
  const es = new EventSource('/api/events');
  const setConn = (cls, text) => { $('conn-dot').className = `dot ${cls}`; $('conn-text').textContent = text; };
  es.onopen = () => { state = freshState(); live = false; setConn('', 'Syncing history…'); };
  es.addEventListener('ready', () => { live = true; renderAll(); setConn('live', 'Live'); });
  es.addEventListener('reset', () => { state = freshState(); renderAll(); });
  es.onmessage = (e) => {
    try { apply(JSON.parse(e.data)); } catch (err) { /* ignore malformed line */ }
  };
  es.onerror = () => setConn('down', 'Reconnecting…');
}

// ------------------------------------------------------------------ GitHub + metrics

function badgeFor(run) {
  if (run.status !== 'completed') return ['running', run.status.replace('_', ' ')];
  if (run.conclusion === 'success') return ['passing', 'passing'];
  if (run.conclusion === 'failure') return ['failing', 'failing'];
  return ['other', run.conclusion || 'done'];
}

async function pollGit() {
  try {
    const g = await (await fetch('/api/git')).json();
    $('gh-branch').textContent = g.branch || '-';
    $('gh-dirty').textContent = g.uncommitted ?? '-';
    const remote = (g.remote || '').replace(/\.git$/, '');
    const link = $('gh-remote');
    link.textContent = remote.replace(/^https:\/\//, '');
    if (/^https:\/\/github\.com\//.test(remote)) link.href = remote; else link.removeAttribute('href');
    const runs = (g.runs || []).map((r) => {
      const li = el('li');
      const [cls, label] = badgeFor(r);
      const a = el('a');
      if (/^https:\/\/github\.com\//.test(r.url)) { a.href = r.url; a.target = '_blank'; a.rel = 'noopener noreferrer'; }
      a.append(el('span', `badge ${cls}`, label), document.createTextNode(r.displayTitle || r.workflowName));
      li.append(a, el('div', 'muted small mono', `${r.headBranch} · ${new Date(r.createdAt).toLocaleString()}`));
      return li;
    });
    $('gh-runs').replaceChildren(...(runs.length ? runs : [el('li', 'muted small', 'No workflow runs yet')]));
    $('gh-commits').replaceChildren(...(g.commits || []).map((c) => {
      const li = el('li');
      li.append(el('span', 'sha', c.sha), document.createTextNode(c.subject));
      const meta = el('div', 'muted small', c.when);
      if (c.refs) meta.append(document.createTextNode('  '), el('span', 'refs', c.refs));
      li.append(meta);
      return li;
    }));
  } catch (e) { /* server restarting */ }
}

function spark(canvas, values, color, max) {
  const dpr = window.devicePixelRatio || 1;
  const w = canvas.clientWidth, h = canvas.clientHeight;
  if (!w) return;
  canvas.width = w * dpr; canvas.height = h * dpr;
  const ctx = canvas.getContext('2d');
  ctx.scale(dpr, dpr);
  ctx.clearRect(0, 0, w, h);
  ctx.strokeStyle = 'rgba(80,160,220,.12)';
  for (let y = 0; y <= h; y += h / 4) { ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke(); }
  if (values.length < 2) return;
  const top = max || Math.max(1, ...values);
  const step = w / 59;
  const pts = values.slice(-60).map((v, i, arr) => [w - (arr.length - 1 - i) * step, h - 4 - (v / top) * (h - 8)]);
  const grad = ctx.createLinearGradient(0, 0, 0, h);
  grad.addColorStop(0, color + '66'); grad.addColorStop(1, color + '00');
  ctx.beginPath(); ctx.moveTo(pts[0][0], h);
  pts.forEach(([x, y]) => ctx.lineTo(x, y));
  ctx.lineTo(pts[pts.length - 1][0], h); ctx.fillStyle = grad; ctx.fill();
  ctx.beginPath(); pts.forEach(([x, y], i) => (i ? ctx.lineTo(x, y) : ctx.moveTo(x, y)));
  ctx.strokeStyle = color; ctx.lineWidth = 1.6; ctx.stroke();
}

const push = (arr, v) => { arr.push(v); if (arr.length > 60) arr.shift(); };

async function pollSystem() {
  try {
    const s = await (await fetch('/api/system')).json();
    const cpu = Math.round(s.cpu * 100), mem = Math.round((s.memUsed / s.memTotal) * 100);
    push(series.cpu, cpu); push(series.mem, mem);
    $('m-cpu').textContent = `${cpu}%`;
    $('m-mem').textContent = `${mem}% of ${(s.memTotal / 1073741824).toFixed(0)} GB`;
  } catch (e) { /* ignore */ }
  const since = Date.now() - 60000;
  const rate = state ? state.events.filter((ev) => Date.parse(ev.ts) > since).length : 0;
  push(series.rate, rate);
  $('m-rate').textContent = String(rate);
  spark($('c-cpu'), series.cpu, '#38d6ff', 100);
  spark($('c-mem'), series.mem, '#ffb347', 100);
  spark($('c-rate'), series.rate, '#b689ff');
}

function tickClock() {
  $('clock').textContent = new Date().toLocaleTimeString();
}

// ------------------------------------------------------------------ boot

state = freshState();
drawNetwork();
renderAll();
$('log-filter-clear').addEventListener('click', () => setFocus(null));
$('agent-card-close').addEventListener('click', () => setFocus(null));
fetch('/api/info').then((r) => r.json()).then((i) => { $('project').textContent = i.project; }).catch(() => {});
connect();
pollGit();
pollSystem();
tickClock();
setInterval(pollGit, 15000);
setInterval(pollSystem, 2000);
setInterval(tickClock, 1000);
