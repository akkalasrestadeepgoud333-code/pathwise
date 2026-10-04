// Traversal Race view: draws the round's tree as SVG and turns taps (or Enter/Space) into actions.
import { createSession } from "./rules.js";

const SVG = "http://www.w3.org/2000/svg";
const RULES = {
  preorder: "Preorder: the node, then its left subtree, then its right subtree.",
  inorder: "Inorder: left subtree, then the node, then the right subtree.",
  postorder: "Postorder: left subtree, then right subtree, then the node.",
  level: "Level order: top to bottom, left to right.",
};
const TITLES = { preorder: "Preorder", inorder: "Inorder", postorder: "Postorder", level: "Level order" };

function el(name, attrs = {}, ns = SVG) {
  const e = document.createElementNS(ns, name);
  for (const [k, v] of Object.entries(attrs)) e.setAttribute(k, String(v));
  return e;
}

/** x from inorder position, y from depth: a tidy layout for small trees. */
function layout(round) {
  const byValue = new Map(round.nodes.map((n) => [n.value, n]));
  const pos = new Map();
  let x = 0;
  let maxDepth = 0;
  (function walk(v, depth) {
    if (v === null || v === undefined) return;
    const n = byValue.get(v);
    walk(n.left, depth + 1);
    pos.set(v, { x: x++, depth });
    maxDepth = Math.max(maxDepth, depth);
    walk(n.right, depth + 1);
  })(round.root, 0);
  return { pos, count: x, maxDepth, byValue };
}

export function mount(stage, puzzle, api) {
  const session = createSession(puzzle);
  const wrap = document.createElement("div");
  wrap.className = "tr";
  const head = document.createElement("div");
  head.className = "tr-head";
  const title = document.createElement("h2");
  const rule = document.createElement("p");
  rule.className = "muted";
  head.append(title, rule);
  const seq = document.createElement("ol");
  seq.className = "tr-seq";
  seq.setAttribute("aria-label", "Your order so far");
  const svgHost = document.createElement("div");
  svgHost.className = "tr-board";
  wrap.append(head, svgHost, seq);
  stage.append(wrap);

  let nodeEls = new Map();
  let busy = false; // between rounds: taps would belong to the next tree

  function draw() {
    const r = puzzle.rounds[session.round];
    title.textContent = `Round ${session.round + 1} of ${puzzle.rounds.length}: ${TITLES[r.order]}`;
    rule.textContent = RULES[r.order];
    api.progress(`Round ${session.round + 1} of ${puzzle.rounds.length}`);
    seq.replaceChildren();
    const { pos, count, maxDepth } = layout(r);
    const W = Math.max(count, 3) * 64;
    const H = (maxDepth + 1) * 84 + 36;
    const svg = el("svg", { viewBox: `0 0 ${W} ${H}`, class: "tr-svg", role: "group", "aria-label": "Tree: tap the nodes in order" });
    const cx = (v) => pos.get(v).x * 64 + 32;
    const cy = (v) => pos.get(v).depth * 84 + 52; // room above the root for its order badge
    for (const n of r.nodes) {
      for (const child of [n.left, n.right]) {
        if (child !== null && child !== undefined) {
          svg.append(el("line", { x1: cx(n.value), y1: cy(n.value), x2: cx(child), y2: cy(child), class: "tr-edge" }));
        }
      }
    }
    nodeEls = new Map();
    for (const n of r.nodes) {
      const g = el("g", { class: "tr-node", tabindex: 0, role: "button", "aria-label": `Node ${n.value}`, transform: `translate(${cx(n.value)} ${cy(n.value)})` });
      g.append(el("circle", { r: 24 }));
      const t = el("text", { "text-anchor": "middle", dy: "0.35em" });
      t.textContent = String(n.value);
      const badge = el("text", { class: "tr-badge", "text-anchor": "middle", y: -30 });
      g.append(t, badge);
      const tap = () => onTap(n.value);
      g.addEventListener("click", tap);
      g.addEventListener("keydown", (e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          tap();
        }
      });
      nodeEls.set(n.value, g);
      svg.append(g);
    }
    svgHost.replaceChildren(svg);
  }

  function mark(value, cls) {
    const g = nodeEls.get(value);
    if (!g) return;
    g.classList.add(cls);
    g.setAttribute("aria-disabled", "true");
    g.querySelector(".tr-badge").textContent = String(seq.children.length);
  }

  function onTap(value) {
    if (busy) return;
    const roundBefore = session.round;
    const g = nodeEls.get(value);
    if (g && g.getAttribute("aria-disabled") === "true") return;
    const outcome = api.act({ type: "tap", a: String(value) });
    if (!outcome) return;
    if (outcome.correct) {
      seq.append(Object.assign(document.createElement("li"), { textContent: String(value), className: outcome.first ? "ok" : "late" }));
      mark(value, outcome.first ? "is-done" : "is-late");
      api.feedback(outcome.first ? "Yes." : "Got it on the second try.", "ok");
    } else if (outcome.revealed !== undefined) {
      seq.append(Object.assign(document.createElement("li"), { textContent: String(outcome.revealed), className: "revealed" }));
      mark(outcome.revealed, "is-revealed");
      api.feedback(`Not ${value}. The next node was ${outcome.revealed}, so it's been filled in.`, "miss");
    } else {
      if (g) {
        g.classList.remove("is-wrong");
        void g.getBoundingClientRect();
        g.classList.add("is-wrong");
        setTimeout(() => g.classList.remove("is-wrong"), 500);
      }
      api.feedback(`Not ${value}. Try another node.`, "miss");
    }
    if (outcome.roundDone && !session.isOver() && session.round !== roundBefore) {
      busy = true;
      setTimeout(() => {
        draw();
        busy = false;
      }, 700);
    }
  }

  draw();
  return session;
}
