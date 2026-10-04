// Bug Hunt rules: a mirror of pathwise-core BugHunt (the server's copy is authoritative).

export const LINE_FIRST = 10;
export const LINE_AFTER_MISS = 5;
export const CLASS_RIGHT = 10;

export class InvalidAction extends Error {}

export function lineCount(snippet) {
  return snippet.code.split("\n").length;
}

export function createSession(puzzle) {
  const classes = new Map(puzzle.classes.map((c) => [c.key, c.label]));
  const events = [];
  let round = 0;
  let lineFound = false;
  let lineMisses = 0;
  let linePart = 0;
  let score = 0;
  let perfect = 0;

  function note(s, part, right) {
    const line = part === 0.5 ? `found line ${s.bugLine} first time`
      : part > 0 ? `found line ${s.bugLine} after a miss` : `line ${s.bugLine} was revealed`;
    return `${s.title}: ${line}` + (right ? ", and named the bug" : `, but the bug was "${classes.get(s.bugClass)}"`);
  }

  return {
    get round() { return round; },
    get lineFound() { return lineFound; },
    apply(action) {
      const s = puzzle.rounds[round];
      if (action.type === "line") {
        if (lineFound) throw new InvalidAction("line already found");
        if (!/^-?\d+$/.test(String(action.a))) throw new InvalidAction("not a line number");
        const line = Number(action.a);
        if (line < 1 || line > lineCount(s)) throw new InvalidAction("no such line");
        if (line === s.bugLine) {
          const first = lineMisses === 0;
          score += first ? LINE_FIRST : LINE_AFTER_MISS;
          linePart = first ? 0.5 : 0.25;
          lineFound = true;
          return { phase: "line", correct: true, first };
        }
        lineMisses++;
        if (lineMisses >= 2) {
          linePart = 0;
          lineFound = true;
          return { phase: "line", correct: false, revealed: s.bugLine };
        }
        return { phase: "line", correct: false };
      }
      if (action.type === "class") {
        if (!lineFound) throw new InvalidAction("find the line first");
        if (!classes.has(action.a)) throw new InvalidAction("unknown bug class");
        const right = s.bugClass === action.a;
        if (right) score += CLASS_RIGHT;
        const partial = linePart + (right ? 0.5 : 0);
        if (partial === 1.0) perfect++;
        events.push({ skillKey: s.skill, correct: partial === 1.0, partial, note: note(s, linePart, right) });
        round++;
        lineFound = false;
        lineMisses = 0;
        linePart = 0;
        return { phase: "class", correct: right, snippet: s, label: classes.get(s.bugClass) };
      }
      throw new InvalidAction("unknown action " + action.type);
    },
    isOver() { return round >= puzzle.rounds.length; },
    result() {
      const n = puzzle.rounds.length;
      return { score, maxScore: n * (LINE_FIRST + CLASS_RIGHT), accuracy: n === 0 ? 0 : perfect / n, events: events.slice() };
    },
  };
}
