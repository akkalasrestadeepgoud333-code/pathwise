// Pathwise interactive visuals. Each <figure data-visual data-spec="{...}"> becomes a small,
// keyboard-operable simulation. Specs are validated on the server at import time.
// Safety: every value from the spec is written with textContent / setAttribute, never innerHTML.

const SVG_NS = "http://www.w3.org/2000/svg";
const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

function svg(tag, attrs = {}, parent) {
  const n = document.createElementNS(SVG_NS, tag);
  for (const [k, v] of Object.entries(attrs)) n.setAttribute(k, String(v));
  if (parent) parent.appendChild(n);
  return n;
}

function el(tag, attrs = {}, text) {
  const n = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) n.setAttribute(k, String(v));
  if (text !== undefined) n.textContent = text;
  return n;
}

function button(label, onClick, extra = {}) {
  const b = el("button", { type: "button", class: "btn btn-sm", ...extra }, label);
  b.addEventListener("click", onClick);
  return b;
}

/** Shared frame: stage (SVG area), status line (aria-live), sequence chips, controls. */
function frame(figure, { sequenceLabel } = {}) {
  figure.querySelector("[data-fallback]")?.remove();
  const controls = el("div", { class: "visual-controls" });
  const stage = el("div", { class: "visual-stage" });
  const status = el("p", { class: "visual-status", "aria-live": "polite" });
  const sequence = el("div", { class: "visual-sequence", "aria-label": sequenceLabel || "Sequence" });
  figure.prepend(controls, stage, status);
  if (sequenceLabel) {
    status.after(sequence);
  }
  return { controls, stage, status, sequence };
}

/** Step-through player used by every animated visual. */
function stepper({ controls, status, steps, render, reset, intro }) {
  let index = -1;
  let timer = null;
  const stepBtn = button("Step ›", () => { stop(); next(); });
  const playBtn = button("Play", () => (timer ? stop() : play()), { "aria-pressed": "false" });
  const resetBtn = button("Reset", () => { stop(); restart(); });
  controls.append(stepBtn, playBtn, resetBtn);

  function next() {
    if (index >= steps().length - 1) { stop(); return false; }
    index++;
    render(index);
    status.textContent = steps()[index].note;
    stepBtn.disabled = index >= steps().length - 1;
    return true;
  }
  function play() {
    if (index >= steps().length - 1) restart();
    playBtn.textContent = "Pause";
    playBtn.setAttribute("aria-pressed", "true");
    const tick = () => { if (!next()) stop(); };
    tick();
    timer = setInterval(tick, reduceMotion ? 1600 : 1100);
  }
  function stop() {
    if (timer) clearInterval(timer);
    timer = null;
    playBtn.textContent = "Play";
    playBtn.setAttribute("aria-pressed", "false");
  }
  function restart() {
    index = -1;
    reset();
    status.textContent = intro();
    stepBtn.disabled = false;
  }
  restart();
  return { restart, stop };
}

function chip(sequence, text) {
  sequence.appendChild(el("span", { class: "seq-chip" }, text));
}

function clearSequence(sequence, label) {
  sequence.replaceChildren(el("span", { class: "seq-label" }, label));
}

// ---------------------------------------------------------------- binary trees (level-order arrays)

function treeLayout(values) {
  const maxIndex = values.length - 1;
  const depth = Math.floor(Math.log2(maxIndex + 1));
  const width = Math.max(320, Math.pow(2, depth) * 64);
  const pos = {};
  values.forEach((v, i) => {
    if (v === null || v === undefined) return;
    const d = Math.floor(Math.log2(i + 1));
    const p = i - (Math.pow(2, d) - 1);
    pos[i] = { x: ((p + 0.5) * width) / Math.pow(2, d), y: 36 + d * 72 };
  });
  return { pos, width, height: 36 + depth * 72 + 40 };
}

function drawTree(stage, values) {
  const { pos, width, height } = treeLayout(values);
  const root = svg("svg", { viewBox: `0 0 ${width} ${height}`, width, height, role: "img", "aria-hidden": "true" });
  const nodes = {};
  for (const i of Object.keys(pos).map(Number)) {
    for (const c of [2 * i + 1, 2 * i + 2]) {
      if (pos[c]) svg("line", { class: "v-edge", x1: pos[i].x, y1: pos[i].y, x2: pos[c].x, y2: pos[c].y }, root);
    }
  }
  for (const i of Object.keys(pos).map(Number)) {
    const g = svg("g", { class: "v-node", transform: `translate(${pos[i].x} ${pos[i].y})` }, root);
    svg("circle", { r: 20 }, g);
    svg("text", {}, g).textContent = String(values[i]);
    nodes[i] = g;
  }
  stage.replaceChildren(root);
  return nodes;
}

const has = (values, i) => i < values.length && values[i] !== null && values[i] !== undefined;

function traversal(values, order) {
  const out = [];
  const v = (i) => values[i];
  const walk = (i) => {
    if (!has(values, i)) return;
    const L = 2 * i + 1, R = 2 * i + 2;
    if (order === "preorder") {
      out.push({ node: i, note: `Visit ${v(i)} first, then its left subtree, then its right subtree.` });
      walk(L); walk(R);
    } else if (order === "inorder") {
      walk(L);
      out.push({ node: i, note: has(values, L) ? `Left subtree of ${v(i)} is finished, so visit ${v(i)}, then go right.` : `${v(i)} has no left child, so visit it now, then go right.` });
      walk(R);
    } else {
      walk(L); walk(R);
      out.push({ node: i, note: has(values, L) || has(values, R) ? `Both subtrees of ${v(i)} are finished, so visit ${v(i)} last.` : `${v(i)} is a leaf: visit it.` });
    }
  };
  if (order === "level-order") {
    const queue = [0];
    while (queue.length) {
      const i = queue.shift();
      if (!has(values, i)) continue;
      const kids = [2 * i + 1, 2 * i + 2].filter((c) => has(values, c));
      queue.push(...kids);
      const waiting = queue.filter((q) => has(values, q)).map(v);
      out.push({ node: i, note: `Visit ${v(i)} (level ${Math.floor(Math.log2(i + 1))}). Queue now: ${waiting.length ? waiting.join(", ") : "empty"}.` });
    }
  } else {
    walk(0);
  }
  return out;
}

const ORDER_NAMES = { preorder: "Preorder", inorder: "Inorder", postorder: "Postorder", "level-order": "Level order" };

function renderTree(figure, spec) {
  const { stage, status } = frame(figure);
  drawTree(stage, spec.tree);
  status.textContent = spec.caption || "";
}

function renderTraversal(figure, spec) {
  const parts = frame(figure, { sequenceLabel: "Visit order" });
  const tabs = el("div", { class: "visual-tabs", role: "group", "aria-label": "Traversal order" });
  parts.controls.append(tabs, el("span", { class: "spacer" }));
  let order = spec.orders[0];
  let nodes = {};
  let steps = traversal(spec.tree, order);
  const player = stepper({
    controls: parts.controls,
    status: parts.status,
    steps: () => steps,
    intro: () => `${ORDER_NAMES[order]}: press Step or Play to walk through the tree.`,
    reset: () => {
      nodes = drawTree(parts.stage, spec.tree);
      clearSequence(parts.sequence, ORDER_NAMES[order]);
    },
    render: (i) => {
      Object.values(nodes).forEach((n) => n.classList.remove("active"));
      const n = nodes[steps[i].node];
      n.classList.add("active", "visited");
      chip(parts.sequence, String(spec.tree[steps[i].node]));
    },
  });
  for (const o of spec.orders) {
    const b = button(ORDER_NAMES[o], () => {
      order = o;
      steps = traversal(spec.tree, order);
      tabs.querySelectorAll("button").forEach((x) => x.setAttribute("aria-pressed", String(x === b)));
      player.stop();
      player.restart();
    }, { "aria-pressed": String(o === order) });
    b.className = "";
    tabs.appendChild(b);
  }
  if (spec.orders.length === 1) tabs.hidden = true;
}

// ---------------------------------------------------------------- BST insertion

function renderBstInsert(figure, spec) {
  const parts = frame(figure, { sequenceLabel: "Inserted" });
  let root = null;
  let steps = [];
  {
    // Pre-compute steps on a scratch tree.
    let scratch = null;
    for (const value of spec.values) {
      if (!scratch) {
        scratch = { value, left: null, right: null };
        steps.push({ value, path: [], placed: true, note: `The tree is empty, so ${value} becomes the root.` });
        continue;
      }
      let cur = scratch;
      const path = [];
      while (true) {
        path.push(cur.value);
        if (value === cur.value) {
          steps.push({ value, path: [...path], placed: false, duplicate: true, note: `${value} is already in the tree. This BST ignores duplicates.` });
          break;
        }
        const dir = value < cur.value ? "left" : "right";
        steps.push({ value, path: [...path], placed: false, note: `${value} ${value < cur.value ? "<" : ">"} ${cur.value}, so go ${dir}.` });
        if (!cur[dir]) {
          cur[dir] = { value, left: null, right: null };
          steps.push({ value, path: [...path], placed: true, note: `The ${dir} spot under ${cur.value} is empty: place ${value} there.` });
          break;
        }
        cur = cur[dir];
      }
    }
  }
  const insert = (value) => {
    if (!root) { root = { value, left: null, right: null }; return; }
    let cur = root;
    while (value !== cur.value) {
      const dir = value < cur.value ? "left" : "right";
      if (!cur[dir]) { cur[dir] = { value, left: null, right: null }; return; }
      cur = cur[dir];
    }
  };
  const draw = (highlightPath, newValue) => {
    const items = [];
    let rank = 0;
    let maxDepth = 0;
    const walk = (n, d) => {
      if (!n) return;
      walk(n.left, d + 1);
      items.push({ n, d, r: rank++ });
      maxDepth = Math.max(maxDepth, d);
      walk(n.right, d + 1);
    };
    walk(root, 0);
    const width = Math.max(320, (items.length + 1) * 52);
    const height = 36 + maxDepth * 70 + 44;
    const s = svg("svg", { viewBox: `0 0 ${width} ${height}`, width, height, "aria-hidden": "true" });
    const at = new Map(items.map((it) => [it.n, { x: ((it.r + 1) * width) / (items.length + 1), y: 36 + it.d * 70 }]));
    for (const it of items) {
      for (const c of [it.n.left, it.n.right]) {
        if (c) svg("line", { class: "v-edge", x1: at.get(it.n).x, y1: at.get(it.n).y, x2: at.get(c).x, y2: at.get(c).y }, s);
      }
    }
    for (const it of items) {
      const cls = ["v-node"];
      if (highlightPath.includes(it.n.value)) cls.push("path");
      if (it.n.value === newValue) cls.push("new");
      const g = svg("g", { class: cls.join(" "), transform: `translate(${at.get(it.n).x} ${at.get(it.n).y})` }, s);
      svg("circle", { r: 20 }, g);
      svg("text", {}, g).textContent = String(it.n.value);
    }
    if (!items.length) {
      svg("text", { x: width / 2, y: height / 2, class: "v-label", "text-anchor": "middle" }, s).textContent = "(empty tree)";
    }
    parts.stage.replaceChildren(s);
  };
  stepper({
    controls: parts.controls,
    status: parts.status,
    steps: () => steps,
    intro: () => `Insert ${spec.values.join(", ")} in that order. Each value walks down from the root.`,
    reset: () => { root = null; draw([], null); clearSequence(parts.sequence, "Inserted"); },
    render: (i) => {
      const st = steps[i];
      if (st.placed) {
        insert(st.value);
        chip(parts.sequence, String(st.value));
      }
      draw(st.path, st.placed ? st.value : null);
    },
  });
}

// ---------------------------------------------------------------- arrays and linked lists (static)

function renderArray(figure, spec) {
  const { stage, status } = frame(figure);
  const w = 56, h = 48, pad = 20;
  const width = pad * 2 + spec.values.length * w;
  const s = svg("svg", { viewBox: `0 0 ${width} 120`, width, height: 120, "aria-hidden": "true" });
  const hl = new Set(spec.highlight || []);
  spec.values.forEach((v, i) => {
    const g = svg("g", { class: "v-box" + (hl.has(i) ? " hl" : ""), transform: `translate(${pad + i * w} 30)` }, s);
    svg("rect", { width: w, height: h, rx: 6 }, g);
    svg("text", { x: w / 2, y: h / 2 }, g).textContent = String(v);
    svg("text", { class: "v-index", x: pad + i * w + w / 2, y: 98 }, s).textContent = String(i);
  });
  for (const [label, index] of Object.entries(spec.pointers || {})) {
    svg("text", { class: "v-label", x: pad + index * w + w / 2, y: 18, "text-anchor": "middle" }, s).textContent = `${label} ↓`;
  }
  stage.replaceChildren(s);
  status.textContent = spec.caption || "";
}

function arrowDefs(s) {
  const defs = svg("defs", {}, s);
  const m = svg("marker", { id: "pw-arrow", viewBox: "0 0 10 10", refX: 9, refY: 5, markerWidth: 7, markerHeight: 7, orient: "auto-start-reverse" }, defs);
  svg("path", { d: "M0 0 L10 5 L0 10 z", class: "v-arrowhead" }, m);
}

function renderLinkedList(figure, spec) {
  const { stage, status } = frame(figure);
  const w = 64, h = 46, gap = 44, pad = 24;
  const width = pad * 2 + spec.values.length * (w + gap) + 40;
  const s = svg("svg", { viewBox: `0 0 ${width} 120`, width, height: 120, "aria-hidden": "true" });
  arrowDefs(s);
  const hl = new Set(spec.highlight || []);
  spec.values.forEach((v, i) => {
    const x = pad + i * (w + gap);
    const g = svg("g", { class: "v-box" + (hl.has(i) ? " hl" : ""), transform: `translate(${x} 40)` }, s);
    svg("rect", { width: w, height: h, rx: 8 }, g);
    svg("line", { x1: w - 18, y1: 0, x2: w - 18, y2: h, class: "v-edge" }, g);
    svg("text", { x: (w - 18) / 2, y: h / 2 }, g).textContent = String(v);
    svg("line", { class: "v-arrow", x1: x + w - 9, y1: 63, x2: x + w + gap - 4, y2: 63, "marker-end": "url(#pw-arrow)" }, s);
  });
  svg("text", { class: "v-label", x: pad + spec.values.length * (w + gap) + 6, y: 67 }, s).textContent = "null";
  svg("text", { class: "v-label", x: pad, y: 28 }, s).textContent = "head ↓";
  stage.replaceChildren(s);
  status.textContent = spec.caption || "";
}

// ---------------------------------------------------------------- stacks and queues

function renderStackQueue(figure, spec) {
  const parts = frame(figure);
  const isStack = spec.mode === "stack";
  let items = [];
  const steps = [];
  {
    const sim = [];
    for (const op of spec.ops) {
      const [verb, arg] = op.split(" ");
      if (verb === "push" || verb === "enqueue") {
        sim.push(arg);
        steps.push({ op, note: isStack ? `push ${arg}: ${arg} goes on top.` : `enqueue ${arg}: ${arg} joins the back of the queue.` });
      } else if (verb === "pop" || verb === "dequeue") {
        const out = isStack ? sim.pop() : sim.shift();
        steps.push({ op, note: out === undefined ? `${verb}: the ${isStack ? "stack" : "queue"} is empty, so this would be an error (underflow).`
          : isStack ? `pop: removes ${out}, the most recently pushed item (LIFO).` : `dequeue: removes ${out}, the item that waited longest (FIFO).` });
      } else {
        const top = isStack ? sim[sim.length - 1] : sim[0];
        steps.push({ op, note: top === undefined ? "peek: nothing to look at; it's empty." : `peek: looks at ${top} without removing it.` });
      }
    }
  }
  const draw = (peek) => {
    const w = 64, h = 40;
    const n = Math.max(items.length, 1);
    const width = isStack ? 220 : Math.max(320, 60 + n * (w + 8));
    const height = isStack ? 40 + n * (h + 6) + 20 : 130;
    const s = svg("svg", { viewBox: `0 0 ${width} ${height}`, width, height, "aria-hidden": "true" });
    items.forEach((v, i) => {
      const isEnd = isStack ? i === items.length - 1 : i === 0;
      const x = isStack ? 78 : 30 + i * (w + 8);
      const y = isStack ? 20 + (items.length - 1 - i) * (h + 6) : 44;
      const g = svg("g", { class: "v-box" + (isEnd && peek ? " top" : isEnd ? " hl" : ""), transform: `translate(${x} ${y})` }, s);
      svg("rect", { width: w, height: h, rx: 6 }, g);
      svg("text", { x: w / 2, y: h / 2 }, g).textContent = String(v);
    });
    if (!items.length) svg("text", { class: "v-label", x: width / 2, y: height / 2, "text-anchor": "middle" }, s).textContent = "(empty)";
    if (items.length) {
      if (isStack) svg("text", { class: "v-label", x: 10, y: 44 }, s).textContent = "top →";
      else {
        svg("text", { class: "v-label", x: 30, y: 30 }, s).textContent = "front";
        svg("text", { class: "v-label", x: 30 + (items.length - 1) * (w + 8), y: 108 }, s).textContent = "back";
      }
    }
    parts.stage.replaceChildren(s);
  };
  stepper({
    controls: parts.controls,
    status: parts.status,
    steps: () => steps,
    intro: () => `A ${spec.mode} with ${spec.ops.length} operations. Press Step to run them one by one.`,
    reset: () => { items = []; draw(false); },
    render: (i) => {
      const [verb, arg] = spec.ops[i].split(" ");
      if (verb === "push" || verb === "enqueue") items.push(arg);
      else if (verb === "pop") items.pop();
      else if (verb === "dequeue") items.shift();
      draw(verb === "peek");
    },
  });
}

// ---------------------------------------------------------------- graph search (BFS / DFS)

function renderGraphSearch(figure, spec) {
  const parts = frame(figure, { sequenceLabel: "Visited" });
  const adj = new Map(spec.nodes.map((n) => [n, []]));
  for (const [a, b] of spec.edges) {
    adj.get(a).push(b);
    if (!spec.directed) adj.get(b).push(a);
  }
  for (const list of adj.values()) list.sort();
  const W = 420, H = 300;
  const pos = spec.positions || Object.fromEntries(spec.nodes.map((n, i) => {
    const a = (2 * Math.PI * i) / spec.nodes.length - Math.PI / 2;
    return [n, [W / 2 + 120 * Math.cos(a), H / 2 + 110 * Math.sin(a)]];
  }));
  const search = (algo) => {
    const steps = [];
    const visited = new Set();
    if (algo === "bfs") {
      const queue = [spec.start];
      visited.add(spec.start);
      while (queue.length) {
        const n = queue.shift();
        const added = [];
        for (const m of adj.get(n)) if (!visited.has(m)) { visited.add(m); queue.push(m); added.push(m); }
        steps.push({ node: n, frontier: [...queue], note: `Visit ${n}. ${added.length ? "Add unvisited " + (added.length === 1 ? "neighbour " : "neighbours ") + added.join(", ") + " to the back of the queue." : "No new neighbours."} Queue: ${queue.length ? queue.join(", ") : "empty"}.` });
      }
    } else {
      const stack = [spec.start];
      while (stack.length) {
        const n = stack.pop();
        if (visited.has(n)) continue;
        visited.add(n);
        const toPush = adj.get(n).filter((m) => !visited.has(m)).reverse();
        stack.push(...toPush);
        steps.push({ node: n, frontier: [...stack].reverse(), note: `Visit ${n}. ${toPush.length ? "Push unvisited neighbours onto the stack." : "Nothing new to push."} Stack (top first): ${stack.length ? [...stack].reverse().join(", ") : "empty"}.` });
      }
    }
    return steps;
  };
  const tabs = el("div", { class: "visual-tabs", role: "group", "aria-label": "Algorithm" });
  parts.controls.append(tabs, el("span", { class: "spacer" }));
  let algo = "bfs";
  let steps = search(algo);
  let nodes = {};
  const draw = () => {
    const s = svg("svg", { viewBox: `0 0 ${W} ${H}`, width: W, height: H, "aria-hidden": "true" });
    for (const [a, b] of spec.edges) svg("line", { class: "v-edge", x1: pos[a][0], y1: pos[a][1], x2: pos[b][0], y2: pos[b][1] }, s);
    nodes = {};
    for (const n of spec.nodes) {
      const g = svg("g", { class: "v-node", transform: `translate(${pos[n][0]} ${pos[n][1]})` }, s);
      svg("circle", { r: 20 }, g);
      svg("text", {}, g).textContent = n;
      nodes[n] = g;
    }
    parts.stage.replaceChildren(s);
  };
  const player = stepper({
    controls: parts.controls,
    status: parts.status,
    steps: () => steps,
    intro: () => `${algo === "bfs" ? "Breadth-first search uses a queue" : "Depth-first search uses a stack"}, starting from ${spec.start}.`,
    reset: () => { draw(); clearSequence(parts.sequence, "Visited"); },
    render: (i) => {
      Object.values(nodes).forEach((g) => g.classList.remove("active"));
      nodes[steps[i].node].classList.add("active", "visited");
      chip(parts.sequence, steps[i].node);
    },
  });
  for (const [key, label] of [["bfs", "BFS (queue)"], ["dfs", "DFS (stack)"]]) {
    const b = button(label, () => {
      algo = key;
      steps = search(algo);
      tabs.querySelectorAll("button").forEach((x) => x.setAttribute("aria-pressed", String(x === b)));
      player.stop();
      player.restart();
    }, { "aria-pressed": String(key === algo) });
    b.className = "";
    tabs.appendChild(b);
  }
}

// ---------------------------------------------------------------- hash table with chaining

function renderHashTable(figure, spec) {
  const parts = frame(figure);
  const n = spec.buckets;
  let table = [];
  const steps = [];
  {
    const sim = Array.from({ length: n }, () => []);
    for (const k of spec.keys) {
      const b = ((k % n) + n) % n;
      const collision = sim[b].length > 0;
      sim[b].push(k);
      steps.push({ key: k, bucket: b, note: `${k} mod ${n} = ${b}, so ${k} goes in bucket ${b}${collision ? ". Collision: it's chained after " + sim[b].slice(0, -1).join(", ") + "." : "."}` });
    }
  }
  const draw = (activeBucket, activeKey) => {
    const rowH = 40, w = 52;
    const longest = Math.max(1, ...table.map((c) => c.length));
    const width = 80 + longest * (w + 30) + 20;
    const height = 16 + n * rowH;
    const s = svg("svg", { viewBox: `0 0 ${width} ${height}`, width, height, "aria-hidden": "true" });
    arrowDefs(s);
    for (let b = 0; b < n; b++) {
      const y = 8 + b * rowH;
      const g = svg("g", { class: "v-box" + (b === activeBucket ? " hl" : ""), transform: `translate(10 ${y})` }, s);
      svg("rect", { width: 44, height: 32, rx: 6 }, g);
      svg("text", { x: 22, y: 16 }, g).textContent = String(b);
      (table[b] || []).forEach((k, j) => {
        const x = 80 + j * (w + 30);
        svg("line", { class: "v-arrow", x1: x - 26, y1: y + 16, x2: x - 4, y2: y + 16, "marker-end": "url(#pw-arrow)" }, s);
        const kg = svg("g", { class: "v-box" + (k === activeKey && b === activeBucket ? " top" : ""), transform: `translate(${x} ${y})` }, s);
        svg("rect", { width: w, height: 32, rx: 6 }, kg);
        svg("text", { x: w / 2, y: 16 }, kg).textContent = String(k);
      });
    }
    parts.stage.replaceChildren(s);
  };
  stepper({
    controls: parts.controls,
    status: parts.status,
    steps: () => steps,
    intro: () => `Insert ${spec.keys.join(", ")} into ${n} buckets using h(k) = k mod ${n}.`,
    reset: () => { table = Array.from({ length: n }, () => []); draw(-1, null); },
    render: (i) => {
      const st = steps[i];
      table[st.bucket].push(st.key);
      draw(st.bucket, st.key);
    },
  });
}

// ---------------------------------------------------------------- boot

const RENDERERS = {
  tree: renderTree,
  traversal: renderTraversal,
  "bst-insert": renderBstInsert,
  array: renderArray,
  "linked-list": renderLinkedList,
  "stack-queue": renderStackQueue,
  "graph-search": renderGraphSearch,
  "hash-table": renderHashTable,
};

document.querySelectorAll("[data-visual]").forEach((figure) => {
  try {
    const spec = JSON.parse(figure.dataset.spec);
    const render = RENDERERS[spec.type];
    if (render) render(figure, spec);
  } catch (e) {
    // Leave the text description in place if anything goes wrong.
    console.warn("Visual could not be rendered", e);
  }
});
