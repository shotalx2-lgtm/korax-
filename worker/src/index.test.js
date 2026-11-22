import test from "node:test";
import assert from "node:assert/strict";
import { fantasyPosition, numericStat, calculateSeasonFantasyPoints } from "./index.js";

test("fantasyPosition maps provider position names", () => {
  assert.equal(fantasyPosition({ name: "Goalkeeper" }), "GK");
  assert.equal(fantasyPosition({ name: "Defender" }), "DEF");
  assert.equal(fantasyPosition("Midfielder"), "MID");
  assert.equal(fantasyPosition("Attacker"), "FWD");
  assert.equal(fantasyPosition("Coach"), "");
});

test("numericStat reads numeric and nested provider values", () => {
  const details = [
    { type: { name: "Goals" }, value: 4 },
    { type: { name: "Assists" }, value: { total: 3 } },
    { type: { name: "Saves" }, value: { all: { count: 7 } } },
  ];
  assert.equal(numericStat(details, "Goals"), 4);
  assert.equal(numericStat(details, "Assists"), 3);
  assert.equal(numericStat(details, "Saves"), 7);
  assert.equal(numericStat(details, "Yellow Cards"), 0);
});

test("season fantasy scoring applies position-specific goal and clean-sheet values", () => {
  const details = [
    { type: { name: "Appearances" }, value: 2 },
    { type: { name: "Goals" }, value: 1 },
    { type: { name: "Assists" }, value: 1 },
    { type: { name: "Clean Sheets" }, value: 1 },
    { type: { name: "Goals Conceded" }, value: 2 },
    { type: { name: "Yellow Cards" }, value: 1 },
    { type: { name: "Red Cards" }, value: 1 },
    { type: { name: "Own Goals" }, value: 1 },
  ];
  assert.equal(calculateSeasonFantasyPoints("DEF", details), 10);
  assert.equal(calculateSeasonFantasyPoints("MID", details), 7);
  assert.equal(calculateSeasonFantasyPoints("FWD", details), 5);
});

test("goalkeeper scoring includes saves and penalty events", () => {
  const details = [
    { type: { name: "Appearances" }, value: 2 },
    { type: { name: "Goals" }, value: 1 },
    { type: { name: "Assists" }, value: 1 },
    { type: { name: "Clean Sheets" }, value: 1 },
    { type: { name: "Saves" }, value: 6 },
    { type: { name: "Penalty Saves" }, value: 1 },
    { type: { name: "Penalties Missed" }, value: 1 },
    { type: { name: "Yellow Cards" }, value: 1 },
    { type: { name: "Red Cards" }, value: 1 },
    { type: { name: "Own Goals" }, value: 1 },
    { type: { name: "Goals Conceded" }, value: 2 },
  ];
  assert.equal(calculateSeasonFantasyPoints("GK", details), 15);
});
