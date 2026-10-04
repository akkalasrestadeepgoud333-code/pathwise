// Parity: the browser's game rules must score exactly like the server's (pathwise-core). Both run the
// same vectors from pathwise-core/src/test/resources/game-vectors. Run by GameParityTest (JUnit) via
// `node --test`, or directly: node --test pathwise-web/src/test/js/games.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath, pathToFileURL } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const vectorsDir = join(here, "..", "..", "..", "..", "pathwise-core", "src", "test", "resources", "game-vectors");
const rulesDir = join(here, "..", "..", "main", "webapp", "static", "js", "games");

function expand(game, list) {
  return list.map((a) => {
    if (game === "traversal_race") return { type: "tap", a, b: null };
    if (game === "match_pairs") return { type: "match", a: a[0], b: a[1] };
    return { type: a[0], a: a[1], b: null };
  });
}

for (const game of ["traversal_race", "match_pairs", "bug_hunt"]) {
  const v = JSON.parse(readFileSync(join(vectorsDir, game + ".json"), "utf8"));
  const { createSession } = await import(pathToFileURL(join(rulesDir, game, "rules.js")).href);
  for (const c of v.cases) {
    test(`${game}: ${c.name}`, () => {
      const s = createSession(v.puzzle);
      const actions = expand(game, c.actions);
      if (c.invalid) {
        assert.throws(() => actions.forEach((a) => s.apply(a)));
        return;
      }
      actions.forEach((a) => s.apply(a));
      assert.equal(s.isOver(), true, "game should be over");
      const r = s.result();
      assert.equal(r.score, c.expect.score);
      assert.equal(r.maxScore, c.expect.maxScore);
      assert.ok(Math.abs(r.accuracy - c.expect.accuracy) < 1e-9, `accuracy ${r.accuracy}`);
      assert.equal(r.events.length, c.expect.events.length);
      r.events.forEach((e, i) => {
        const x = c.expect.events[i];
        assert.equal(e.skillKey, x.skillKey);
        assert.equal(e.correct, x.correct);
        assert.ok(Math.abs(e.partial - x.partial) < 1e-9, `partial ${e.partial}`);
      });
    });
  }
}
