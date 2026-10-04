// Bug Hunt view: code lines are buttons; once the line is found (or revealed), the bug classes appear.
import { createSession } from "./rules.js";

export function mount(stage, puzzle, api) {
  const session = createSession(puzzle);
  const wrap = document.createElement("div");
  wrap.className = "bh";
  stage.append(wrap);
  let waiting = false; // showing the explanation; the next snippet starts on "Next"

  function draw(moveFocus) {
    const s = puzzle.rounds[session.round];
    api.progress(`Snippet ${session.round + 1} of ${puzzle.rounds.length}`);
    const title = document.createElement("h2");
    title.textContent = s.title;
    const ask = document.createElement("p");
    ask.className = "muted";
    ask.textContent = "Which line has the bug? Tap it.";
    const code = document.createElement("ol");
    code.className = "bh-code";
    code.setAttribute("aria-label", "Code lines");
    s.code.split("\n").forEach((text, i) => {
      const li = document.createElement("li");
      const b = document.createElement("button");
      b.type = "button";
      b.className = "bh-line";
      b.dataset.line = String(i + 1);
      b.setAttribute("aria-label", `Line ${i + 1}: ${text.trim() || "blank"}`);
      const num = document.createElement("span");
      num.className = "bh-num";
      num.textContent = String(i + 1);
      const src = document.createElement("code");
      src.textContent = text;
      b.append(num, src);
      b.addEventListener("click", () => pickLine(i + 1, b));
      li.append(b);
      code.append(li);
    });
    const classes = document.createElement("div");
    classes.className = "bh-classes";
    classes.hidden = true;
    const q = document.createElement("p");
    q.className = "bh-ask";
    q.textContent = "What kind of bug is it?";
    classes.append(q);
    for (const c of puzzle.classes) {
      const b = document.createElement("button");
      b.type = "button";
      b.className = "btn btn-sm";
      b.textContent = c.label;
      b.addEventListener("click", () => pickClass(c.key));
      classes.append(b);
    }
    const explain = document.createElement("div");
    explain.className = "bh-explain";
    explain.hidden = true;
    wrap.replaceChildren(title, ask, code, classes, explain);
    if (moveFocus) code.querySelector("button")?.focus(); // only after "Next", not on page load
  }

  function lineButton(n) {
    return wrap.querySelector(`.bh-line[data-line="${n}"]`);
  }

  function showClasses() {
    wrap.querySelectorAll(".bh-line").forEach((b) => (b.disabled = true));
    const classes = wrap.querySelector(".bh-classes");
    classes.hidden = false;
    classes.querySelector("button")?.focus();
  }

  function pickLine(n, button) {
    if (waiting || session.lineFound) return;
    const outcome = api.act({ type: "line", a: String(n) });
    if (!outcome) return;
    if (outcome.correct) {
      button.classList.add("is-bug");
      api.feedback(outcome.first ? `Yes, line ${n}.` : `Yes, line ${n} on the second try.`, "ok");
      showClasses();
    } else if (outcome.revealed) {
      button.classList.add("is-wrong");
      lineButton(outcome.revealed).classList.add("is-bug", "is-revealed");
      api.feedback(`Not line ${n}. The bug is on line ${outcome.revealed}.`, "miss");
      showClasses();
    } else {
      button.classList.add("is-wrong");
      button.disabled = true;
      api.feedback(`Line ${n} is fine. One more try.`, "miss");
    }
  }

  function pickClass(key) {
    if (waiting) return;
    const outcome = api.act({ type: "class", a: key });
    if (!outcome) return;
    waiting = true;
    wrap.querySelectorAll(".bh-classes button").forEach((b) => (b.disabled = true));
    api.feedback(outcome.correct ? `Right: ${outcome.label}.` : `It was "${outcome.label}".`, outcome.correct ? "ok" : "miss");
    const explain = wrap.querySelector(".bh-explain");
    const p = document.createElement("p");
    p.textContent = outcome.snippet.explanation;
    explain.replaceChildren(p);
    if (!session.isOver()) {
      const next = document.createElement("button");
      next.type = "button";
      next.className = "btn btn-primary";
      next.textContent = "Next snippet";
      next.addEventListener("click", () => {
        waiting = false;
        draw(true);
      });
      explain.append(next);
      explain.hidden = false;
      next.focus();
    } else {
      explain.hidden = false;
    }
  }

  draw();
  return session;
}
