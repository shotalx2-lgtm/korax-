import test from "node:test";
import assert from "node:assert/strict";
import { normalizeApiFootballFixture, createApiFootballClient } from "./api-football.mjs";

test("normalizes a completed API-Football fixture and stable source identity", () => {
  const fixture = normalizeApiFootballFixture({
    fixture: { id: 900, date: "2026-11-22T18:00:00+00:00", status: { short: "FT" } },
    league: { id: 39, name: "Premier League", season: 2026, round: "Regular Season - 12" },
    teams: { home: { id: 1, name: "Arsenal" }, away: { id: 2, name: "Chelsea" } },
    goals: { home: 2, away: 1 },
  });
  assert.equal(fixture.sourceKey, "api_football");
  assert.equal(fixture.homeScore, 2);
  assert.equal(fixture.awayScore, 1);
  assert.equal(fixture.isFinished, true);
  assert.equal(fixture.isLive, false);
  assert.equal(fixture.externalId, "900");
});

test("does not turn an in-play or scheduled fixture into a finished score", () => {
  const fixture = normalizeApiFootballFixture({
    fixture: { id: 901, date: "2026-11-25T18:00:00+00:00", status: { short: "1H" } },
    league: { id: 39, name: "Premier League", season: 2026 },
    teams: { home: { name: "Arsenal" }, away: { name: "Liverpool" } },
    goals: { home: 1, away: 0 },
  });
  assert.equal(fixture.isLive, true);
  assert.equal(fixture.isFinished, false);
  assert.equal(fixture.homeScore, null);
});

test("requires an API key", () => {
  assert.throws(() => createApiFootballClient(), /API_FOOTBALL_KEY is required/);
});
