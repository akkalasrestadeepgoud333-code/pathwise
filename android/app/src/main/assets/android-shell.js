// Android host for the web's games: same views and rules as the website, puzzle and finish via the app's
// bridge. The app sends the action log to the server, which replays it with the authoritative rules.
const VIEWS = { traversal_race: "./games/traversal_race/view.js", match_pairs: "./games/match_pairs/view.js", bug_hunt: "./games/bug_hunt/view.js" };
const bridge = window.PathwiseBridge;
const root = document.getElementById("game");
const stage = root.querySelector("[data-stage]");
const feedbackEl = root.querySelector("[data-feedback]");
const scoreEl = root.querySelector("[data-score]");
const progressEl = root.querySelector("[data-progress]");

async function boot() {
  const template = bridge.template();
  if (!VIEWS[template]) {
    stage.textContent = "This game isn't available in the app yet.";
    return;
  }
  const puzzle = JSON.parse(bridge.puzzle());
  const minGap = Number(bridge.minGap()) || 300;
  const view = await import(VIEWS[template]);
  const actions = [];
  const t0 = performance.now();
  let last = -Infinity;
  let finished = false;
  let session;
  const api = {
    act(action) {
      if (finished) return null;
      const now = performance.now();
      if (now - last < minGap + 20) return null;
      let outcome;
      try {
        outcome = session.apply({ type: action.type, a: action.a ?? null, b: action.b ?? null });
      } catch (e) {
        return null;
      }
      last = now;
      actions.push({ type: action.type, a: action.a ?? null, b: action.b ?? null, t: Math.round(now - t0) });
      scoreEl.textContent = String(session.result().score);
      if (session.isOver()) {
        finished = true;
        feedbackEl.textContent = "Checking your game…";
        setTimeout(() => bridge.finish(JSON.stringify(actions)), 700);
      }
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
  stage.replaceChildren();
  session = view.mount(stage, puzzle, api);
}

boot();