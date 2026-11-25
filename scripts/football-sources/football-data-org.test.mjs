import test from "node:test";
import assert from "node:assert/strict";
import { normalizeFootballDataMatch, createFootballDataOrgClient } from "./football-data-org.mjs";

test("normalizes a completed Football-Data.org match without fabricating live status", () => {
  const match = normalizeFootballDataMatch({
    id: 77,
    utcDate: "2026-11-22T16:30:00Z",
    status: "FINISHED",
    matchday: 12,
    competition: { name: "Premier League" },
    season: { startDate: "2026-08-01", id: 2026 },
    homeTeam: { id: 1, name: "Arsenal" },
    awayTeam: { id: 2, name: "Chelsea" },
    score: { fullTime: { home: 2, away: 1 } },
  });
  assert.equal(match.sourceKey, "football_data_org");
  assert.equal(match.homeScore, 2);
  assert.equal(match.awayScore, 1);
  assert.equal(match.isFinished, true);
  assert.equal(match.isLive, false);
  assert.equal(match.rawPayload.matchday, 12);
});

test("keeps incomplete and not-yet-finished fixtures scoreless", () => {
  const match = normalizeFootballDataMatch({
    id: 78,
    utcDate: "2026-12-01T19:00:00Z",
    status: "SCHEDULED",
    competition: { name: "Premier League" },
    season: { startDate: "2026-08-01" },
    homeTeam: { name: "Arsenal" },
    awayTeam: { name: "Liverpool" },
    score: { fullTime: { home: 0, away: 0 } },
  });
  assert.equal(match.homeScore, null);
  assert.equal(match.awayScore, null);
  assert.equal(match.isFinished, false);
  assert.equal(match.isLive, false);
});

test("requires a private API token", () => {
  assert.throws(() => createFootballDataOrgClient(), /FOOTBALL_DATA_API_TOKEN is required/);
});
