// Match Pairs rules: a mirror of pathwise-core MatchPairs (the server's copy is authoritative).

export const CLEAN = 10;
export const ONE_MISS = 5;

export class InvalidAction extends Error {}

export function createSession(puzzle) {
  const byId = new Map(puzzle.items.map((i) => [i.id, i]));
  const misses = new Map();
  const matched = new Set();
  const events = [];
  let score = 0;
  let clean = 0;

  return {
    isMatched(id) { return matched.has(id); },
    apply(action) {
      if (action.type !== "match") throw new InvalidAction("unknown action " + action.type);
      const left = action.a;
      const right = action.b;
      if (!byId.has(left) || !byId.has(right)) throw new InvalidAction("no such item");
      if (matched.has(left) || matched.has(right)) throw new InvalidAction("item already matched");
      if (left === right) {
        const m = misses.get(left) || 0;
        score += m === 0 ? CLEAN : m === 1 ? ONE_MISS : 0;
        if (m === 0) clean++;
        matched.add(left);
        const item = byId.get(left);
        const partial = m === 0 ? 1.0 : m === 1 ? 0.5 : 0.0;
        events.push({ skillKey: item.skill, correct: m === 0, partial,
          note: m === 0 ? `Matched "${item.left}" first time` : `Matched "${item.left}" after ${m} ${m === 1 ? "wrong guess" : "wrong guesses"}` });
        return { correct: true, first: m === 0 };
      }
      misses.set(left, (misses.get(left) || 0) + 1);
      return { correct: false };
    },
    isOver() { return matched.size === puzzle.items.length; },
    result() {
      const n = puzzle.items.length;
      return { score, maxScore: n * CLEAN, accuracy: n === 0 ? 0 : clean / n, events: events.slice() };
    },
  };
}
