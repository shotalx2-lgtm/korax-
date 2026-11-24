import test from "node:test";
import assert from "node:assert/strict";
import { filterCurrentFootballLeagues, normalizeOpenLigaMatch, openLigaDbNormalizers } from "./openligadb.mjs";

test("filters out old seasons, test leagues and non-football sports", () => {
  const leagues = [
    { leagueId: 1, leagueName: "1. Fußball-Bundesliga 2026/2027", leagueShortcut: "bl1", leagueSeason: "2026", sport: { sportId: 1 } },
    { leagueId: 2, leagueName: "Bundesliga Test", leagueShortcut: "blTest", leagueSeason: "2026", sport: { sportId: 1 } },
    { leagueId: 3, leagueName: "1. Volleyball Bundesliga 2026", leagueShortcut: "vbl", leagueSeason: "2026", sport: { sportId: 44 } },
    { leagueId: 4, leagueName: "Premier League 2010/2011", leagueShortcut: "PL", leagueSeason: "2010", sport: { sportId: 1 } },
    { leagueId: 5, leagueName: "UEFA Women's Champions League", leagueShortcut: "WCL", leagueSeason: "2026", sport: { sportId: 79 } },
  ];
  const result = filterCurrentFootballLeagues(leagues, { currentYear: 2026 });
  assert.deepEqual(result.map(league => league.leagueId), [1, 5]);
});

test("supports an explicit allowlist for display-safe competition coverage", () => {
  const leagues = [
    { leagueId: 1, leagueName: "Premier League", leagueShortcut: "PL", leagueSeason: "2026", sport: { sportId: 1 } },
    { leagueId: 2, leagueName: "Bundesliga", leagueShortcut: "bl1", leagueSeason: "2026", sport: { sportId: 1 } },
  ];
  assert.deepEqual(
    filterCurrentFootballLeagues(leagues, { currentYear: 2026, allowedShortcuts: ["bl1"] }).map(item => item.leagueId),
    [2],
  );
});

test("normalizes OpenLigaDB scores, goals and a stable match fingerprint", () => {
  const now = new Date();
  const match = normalizeOpenLigaMatch({
    matchID: 99,
    leagueId: 1,
    matchDateTimeUTC: now.toISOString(),
    matchFinished: true,
    group: { groupName: "Matchday 10", groupOrderID: 10 },
    team1: { teamId: 11, teamName: "Arsenal" },
    team2: { teamId: 12, teamName: "Chelsea" },
    matchResults: [
      { resultName: "Halbzeit", pointsTeam1: 1, pointsTeam2: 0, resultID: 1 },
      { resultName: "Endergebnis", pointsTeam1: 2, pointsTeam2: 1, resultID: 2 },
    ],
    goals: [{ matchMinute: 70, goalGetterName: "Example" }],
  }, { leagueName: "Premier League", leagueSeason: "2026" });
  assert.equal(match.externalId, "99");
  assert.equal(match.homeScore, 2);
  assert.equal(match.awayScore, 1);
  assert.equal(match.isFinished, true);
  assert.equal(match.isLive, false);
  assert.equal(match.rawPayload.goals.length, 1);
});

test("normalizes league source IDs and keeps ODbL provenance", () => {
  const league = openLigaDbNormalizers.leagueRecord({
    leagueId: 101,
    leagueName: "UEFA Champions League 2026/27",
    leagueShortcut: "ucl",
    leagueSeason: "2026",
    sport: { sportId: 1, sportName: "Fußball" },
  });
  assert.equal(league.entityType, "tournament");
  assert.equal(league.externalId, "101");
  assert.equal(league.metadata.license, "ODbL-1.0");
});

test("uses provider-reported sport names instead of trusting an unrelated numeric ID", () => {
  const leagues = [
    { leagueId: 1, leagueName: "Premier League", leagueShortcut: "pl", leagueSeason: "2026", sport: { sportId: 1, sportName: "Basketball" } },
    { leagueId: 2, leagueName: "Premier League", leagueShortcut: "pl2", leagueSeason: "2026", sport: { sportId: 9, sportName: "Football" } },
  ];
  assert.deepEqual(
    filterCurrentFootballLeagues(leagues, { currentYear: 2026 }).map(item => item.leagueId),
    [2],
  );
});
