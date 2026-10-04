// GameShell: hosts one game, records every action with its time, and sends the log to the server when the
// game ends. The server replays the log with the authoritative rules, so nothing scored here is trusted;
// these rules exist only to give instant feedback.

const TEMPLATES = { traversal_race: "./traversal_race/view.js", match_pairs: "./match_pairs/view.js", bug_hunt: "./bug_hunt/view.js" };

const root = document.getElementById("game");

function csrfToken() {
  const meta = document.querySelector('meta[name="csrf-token"]');
  return meta ? meta.content : "";
}

async function boot() {
  const template = root.dataset.template;
  if (!TEMPLATES[template]) return;
  const puzzle = JSON.parse(root.dataset.puzzle);
  const relaxed = root.dataset.relaxed === "true";
  const minGap = Number(root.dataset.minGap) || 300;
  const stage = root.querySelector("[data-stage]");
  const feedbackEl = root.querySelector("[data-feedback]");
  const scoreEl = root.querySelector("[data-score]");
  const progressEl = root.querySelector("[data-progress]");
  const clockEl = root.querySelector("[data-clock]");
  const pausedEl = root.querySelector("[data-paused]");

  if (relaxed) {
    root.querySelector("[data-score-wrap]").hidden = true;
    root.querySelector("[data-clock-wrap]").hidden = true;
  }

  const view = await import(TEMPLATES[template]);
  const actions = [];
  const t0 = performance.now();
  let last = -Infinity;
  let finished = false;
  let session;

  const clock = setInterval(() => {
    const s = Math.floor((performance.now() - t0) / 1000);
    clockEl.textContent = Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0");
  }, 1000);

  const api = {
    /** Records and applies one action. Returns the rules' outcome, or null if it was ignored. */
    act(action) {
      if (finished || !pausedEl.hidden) return null;
      const now = performance.now();
      // Double taps faster than the rules allow would make the replay fail; ignore them instead.
      if (now - last < minGap + 20) return null;
      let outcome;
      try {
        outcome = session.apply({ type: action.type, a: action.a ?? null, b: action.b ?? null });
      } catch (e) {
        return null; // impossible here (e.g. an already-matched card): not recorded
      }
      last = now;
      actions.push({ type: action.type, a: action.a ?? null, b: action.b ?? null, t: Math.round(now - t0) });
      scoreEl.textContent = String(session.result().score);
      if (session.isOver()) finish();
      return outcome;
    },
    feedback(text, kind) {
      feedbackEl.textContent = text;
      feedbackEl.dataset.kind = kind || "";
    },
    progress(text) {
      progressEl.textContent = text;
    },
  };

  async function finish() {
    finished = true;
    clearInterval(clock);
    // Let the last feedback be seen before checking.
    await new Promise((r) => setTimeout(r, 900));
    stage.replaceChildren(Object.assign(document.createElement("p"), { className: "muted", textContent: "Checking your game…" }));
    api.feedback("", "");
    try {
      const res = await fetch(root.dataset.finishUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-CSRF-Token": csrfToken(), "X-Requested-With": "fetch" },
        body: JSON.stringify({ actions }),
        credentials: "same-origin",
      });
      const body = await res.json();
      window.location.assign(body.redirect);
    } catch (e) {
      api.feedback("Couldn't reach the server. Check your connection, then reload this page to try again.", "error");
    }
  }

  const pauseBtn = root.querySelector("[data-pause]");
  pauseBtn.addEventListener("click", () => {
    pausedEl.hidden = false;
    stage.hidden = true;
    root.querySelector("[data-resume]").focus();
  });
  root.querySelector("[data-resume]").addEventListener("click", () => {
    pausedEl.hidden = true;
    stage.hidden = false;
  });

  stage.querySelector("[data-loading]")?.remove();
  session = view.mount(stage, puzzle, api);
}

if (root) boot();
