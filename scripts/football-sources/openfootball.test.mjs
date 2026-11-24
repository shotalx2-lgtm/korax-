import test from "node:test";
import assert from "node:assert/strict";
import { normalizeOpenFootballMatch, parseOpenFootballSeason } from "./openfootball.mjs";

test("normalizes finished OpenFootball fixtures without inventing live events", () => {
  const match = normalizeOpenFootballMatch({
    round: "Matchday 12",
    date: "2026-11-22",
    team1: "Arsenal",
    team2: "Chelsea",
    score: { ft: [2, 1], ht: [1, 0] },
  }, { competitionName: "Premier League", season: "2026-27", countryCode: "GB" });
  assert.equal(match.homeScore, 2);
  assert.equal(match.awayScore, 1);
  assert.equal(match.status, "finished");
  assert.equal(match.isLive, false);
  assert.equal(match.isFinished, true);
  assert.equal(match.rawPayload.round, "Matchday 12");
});

test("keeps scheduled fixtures scoreless when no final score exists", () => {
  const match = normalizeOpenFootballMatch({
    round: "Matchday 18",
    date: "2027-01-05",
    team1: "Arsenal",
    team2: "Liverpool",
  }, { competitionName: "Premier League", season: "2026-27", countryCode: "GB" });
  assert.equal(match.homeScore, null);
  assert.equal(match.awayScore, null);
  assert.equal(match.status, "scheduled");
  assert.equal(match.isLive, false);
});

test("parses season files and rejects incomplete rows", () => {
  const result = parseOpenFootballSeason({
    name: "Premier League 2026/27",
    matches: [
      { round: "Matchday 1", date: "2026-08-10", team1: "Team A", team2: "Team B" },
      { date: "bad-date", team1: "Team C", team2: "Team D" },
      { date: "2026-08-11", team1: "", team2: "Team E" },
    ],
  }, { competitionName: "Premier League", season: "2026-27", countryCode: "GB" });
  assert.equal(result.length, 1);
  assert.equal(result[0].competitionName, "Premier League 2026/27");
});
