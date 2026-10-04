// Traversal Race rules: a mirror of pathwise-core TraversalRace (the server's copy is authoritative).
// Parity is checked by the shared vectors in pathwise-core/src/test/resources/game-vectors.

export const FIRST_TRY = 10;
export const AFTER_MISS = 4;
export const MISSES_BEFORE_REVEAL = 2;

export class InvalidAction extends Error {}

const LABELS = { preorder: "preorder", inorder: "inorder", postorder: "postorder", level: "level order" };

export function createSession(puzzle) {
  let round = 0;
  let position = 0;
  let misses = 0;
  let firstTryInRound = 0;
  let firstTryTotal = 0;
  let score = 0;
  const events = [];

  function advance(r) {
    position++;
    misses = 0;
    if (position === r.answer.length) {
      const n = r.answer.length;
      events.push({ skillKey: r.skill, correct: firstTryInRound === n, partial: firstTryInRound / n,
        note: `${firstTryInRound} of ${n} nodes right first time in ${LABELS[r.order]}` });
      round++;
      position = 0;
      firstTryInRound = 0;
      return true;
    }
    return false;
  }

  return {
    get round() { return round; },
    get position() { return position; },
    apply(action) {
      if (action.type !== "tap") throw new InvalidAction("unknown action " + action.type);
      const r = puzzle.rounds[round];
      if (!/^-?\d+$/.test(String(action.a))) throw new InvalidAction("not a node value");
      const value = Number(action.a);
      if (!r.nodes.some((n) => n.value === value)) throw new InvalidAction("no such node " + value);
      if (r.answer.slice(0, position).includes(value)) throw new InvalidAction("node already visited");
      const expected = r.answer[position];
      if (value === expected) {
        const first = misses === 0;
        if (first) {
          score += FIRST_TRY;
          firstTryInRound++;
          firstTryTotal++;
        } else {
          score += AFTER_MISS;
        }
        const roundDone = advance(r);
        return { correct: true, first, value, roundDone };
      }
      misses++;
      if (misses >= MISSES_BEFORE_REVEAL) {
        const roundDone = advance(r);
        return { correct: false, revealed: expected, value, roundDone };
      }
      return { correct: false, value, roundDone: false };
    },
    isOver() { return round >= puzzle.rounds.length; },
    result() {
      const total = puzzle.rounds.reduce((s, r) => s + r.answer.length, 0);
      return { score, maxScore: total * FIRST_TRY, accuracy: total === 0 ? 0 : firstTryTotal / total, events: events.slice() };
    },
  };
}
