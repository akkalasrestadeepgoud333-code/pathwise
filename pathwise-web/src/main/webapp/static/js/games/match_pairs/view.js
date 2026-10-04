// Match Pairs view: pick a term, then its meaning (or the other way round). All plain buttons, so it
// works with a keyboard and screen readers.
import { createSession } from "./rules.js";

export function mount(stage, puzzle, api) {
  const session = createSession(puzzle);
  const wrap = document.createElement("div");
  wrap.className = "mp";
  const hint = document.createElement("p");
  hint.className = "muted";
  hint.textContent = "Choose a term, then the meaning that goes with it.";
  const cols = document.createElement("div");
  cols.className = "mp-cols";
  const left = document.createElement("div");
  left.className = "mp-col";
  left.setAttribute("role", "group");
  left.setAttribute("aria-label", "Terms");
  const right = document.createElement("div");
  right.className = "mp-col";
  right.setAttribute("role", "group");
  right.setAttribute("aria-label", "Meanings");
  cols.append(left, right);
  wrap.append(hint, cols);
  stage.append(wrap);

  const byId = new Map(puzzle.items.map((i) => [i.id, i]));
  const leftBtns = new Map();
  const rightBtns = new Map();
  let selLeft = null;
  let selRight = null;
  let matchedCount = 0;

  function button(text, side, id) {
    const b = document.createElement("button");
    b.type = "button";
    b.className = "mp-card";
    b.textContent = text;
    b.setAttribute("aria-pressed", "false");
    b.addEventListener("click", () => choose(side, id));
    return b;
  }

  for (const item of puzzle.items) {
    const b = button(item.left, "left", item.id);
    leftBtns.set(item.id, b);
    left.append(b);
  }
  for (const id of puzzle.rightOrder) {
    const b = button(byId.get(id).right, "right", id);
    rightBtns.set(id, b);
    right.append(b);
  }

  function setPressed(map, id) {
    for (const [k, b] of map) b.setAttribute("aria-pressed", String(k === id));
  }

  function progress() {
    api.progress(`${matchedCount} of ${puzzle.items.length} matched`);
  }

  function choose(side, id) {
    if (session.isMatched(id)) return;
    if (side === "left") {
      selLeft = id;
      setPressed(leftBtns, id);
    } else {
      selRight = id;
      setPressed(rightBtns, id);
    }
    if (selLeft === null || selRight === null) return;
    const l = selLeft;
    const r = selRight;
    const outcome = api.act({ type: "match", a: l, b: r });
    if (!outcome) return;
    selLeft = null;
    selRight = null;
    setPressed(leftBtns, null);
    setPressed(rightBtns, null);
    if (outcome.correct) {
      matchedCount++;
      for (const b of [leftBtns.get(l), rightBtns.get(r)]) {
        b.classList.add(outcome.first ? "is-matched" : "is-matched-late");
        b.disabled = true;
      }
      api.feedback(`Matched: ${byId.get(l).left} → ${byId.get(l).right}`, "ok");
      progress();
    } else {
      for (const b of [leftBtns.get(l), rightBtns.get(r)]) {
        b.classList.remove("is-wrong");
        void b.offsetWidth;
        b.classList.add("is-wrong");
        setTimeout(() => b.classList.remove("is-wrong"), 500);
      }
      api.feedback(`"${byId.get(l).left}" doesn't go with that one. Try again.`, "miss");
    }
  }

  progress();
  return session;
}
