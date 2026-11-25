import test from "node:test";
import assert from "node:assert/strict";
import { mergeSupplementalMatchFeed } from "../../supabase/functions/korax-api/external-match-feed.mjs";

const baseRow = (overrides = {}) => ({
  source_key: "openfootball",
  external_id: "fixture-123",
  competition_name: "English Championship 2026/27",
  match_date: "2026-11-25",
  kickoff_at: null,
  home_team_name: "Bolton Wanderers FC",
  away_team_name: "Preston North End FC",
  home_score: 2,
  away_score: 1,
  status: "finished",
  is_live: false,
  is_finished: true,
  raw_payload: { time: "19:00" },
  ...overrides,
});

test("maps supplemental finished fixtures with source attribution and no fabricated details", () => {
  const [match] = mergeSupplementalMatchFeed([], [baseRow()], { mode: "finished", scope: "main" });
  assert.equal(match.sourceName, "OpenFootball");
  assert.equal(match.sourceLicense, "CC0-1.0");
  assert.equal(match.homeScore, 2);
  assert.equal(match.awayScore, 1);
  assert.equal(match.startTime, "19:00");
  assert.equal(match.detailAvailable, false);
  assert.deepEqual(match.events, []);
  assert.deepEqual(match.lineups, []);
  assert.deepEqual(match.statistics, []);
});

test("filters by requested match state and never promotes supplemental data to live", () => {
  const upcoming = baseRow({ external_id: "upcoming", is_finished: false, home_score: null, away_score: null });
  const finished = baseRow({ external_id: "finished" });
  const live = baseRow({ external_id: "live", is_live: true, is_finished: false });
  assert.equal(mergeSupplementalMatchFeed([], [upcoming, finished, live], { mode: "upcoming" }).length, 1);
  assert.equal(mergeSupplementalMatchFeed([], [upcoming, finished, live], { mode: "finished" }).length, 1);
  assert.equal(mergeSupplementalMatchFeed([], [upcoming, finished, live], { mode: "live" }).length, 0);
});

test("keeps primary SportScore match and suppresses supplemental duplicate regardless of common club suffixes", () => {
  const primary = [{
    id: "sportscore-123",
    home: "Bolton Wanderers",
    away: "Preston North End",
    startTime: "2026-11-25T19:00:00Z",
    competitionName: "English Championship",
    detailAvailable: true,
  }];
  const result = mergeSupplementalMatchFeed(primary, [baseRow()], { mode: "finished", scope: "all" });
  assert.equal(result.length, 1);
  assert.equal(result[0].id, "sportscore-123");
  assert.equal(result[0].detailAvailable, true);
});

test("deduplicates the same fixture across supplemental providers and prefers OpenLigaDB", () => {
  const openFootball = baseRow();
  const openLiga = baseRow({
    source_key: "openligadb",
    external_id: "555",
    competition_name: "English Championship",
    raw_payload: {},
  });
  const result = mergeSupplementalMatchFeed([], [openFootball, openLiga], { mode: "finished", scope: "all" });
  assert.equal(result.length, 1);
  assert.equal(result[0].sourceName, "OpenLigaDB");
});

test("main scope includes supported top tiers and the newly added Championship and Brazil season", () => {
  const rows = [
    baseRow({ external_id: "championship" }),
    baseRow({
      external_id: "brazil",
      competition_name: "Brasileiro Série A 2026",
      home_team_name: "São Paulo FC",
      away_team_name: "CR Flamengo",
    }),
    baseRow({ external_id: "random", competition_name: "Random Regional League" }),
  ];
  const result = mergeSupplementalMatchFeed([], rows, { mode: "finished", scope: "main" });
  assert.deepEqual(result.map(item => item.externalId), ["championship", "brazil"]);
});

test("rejects malformed records and unknown data providers", () => {
  const result = mergeSupplementalMatchFeed([], [
    baseRow({ external_id: "" }),
    baseRow({ source_key: "unknown" }),
    baseRow({ home_team_name: "" }),
  ], { mode: "all", scope: "all" });
  assert.deepEqual(result, []);
});

test("suppresses explicit canonical source links even when provider naming differs", () => {
  const primary = [{
    id: "sportscore-main-id",
    home: "Some Home Club",
    away: "Some Away Club",
    startTime: "2026-11-25T19:00:00Z",
    detailAvailable: true,
  }];
  const linked = baseRow({
    external_id: "linked-source-id",
    home_team_name: "Different Provider Home Name",
    away_team_name: "Different Provider Away Name",
    canonical_match_slug: "sportscore-main-id",
  });
  const result = mergeSupplementalMatchFeed(primary, [linked], { mode: "finished", scope: "all" });
  assert.equal(result.length, 1);
  assert.equal(result[0].id, "sportscore-main-id");
});

test("maps Football-Data.org supplemental fixtures with visible source attribution", () => {
  const row = baseRow({
    source_key: "football_data_org",
    external_id: "fd-991",
    competition_name: "Premier League",
    home_team_name: "Arsenal",
    away_team_name: "Chelsea",
    home_score: 2,
    away_score: 1,
    raw_payload: {},
  });
  const [match] = mergeSupplementalMatchFeed([], [row], { mode: "finished", scope: "main" });
  assert.equal(match.sourceName, "Football-Data.org");
  assert.match(match.sourceLicense, /attribution required/i);
  assert.equal(match.detailAvailable, false);
});

test("keeps competitions distinct when the same clubs meet on the same date", () => {
  const league = baseRow({
    external_id: "league",
    competition_name: "Premier League",
    home_team_name: "Arsenal",
    away_team_name: "Chelsea",
  });
  const cup = baseRow({
    external_id: "cup",
    competition_name: "FA Cup",
    home_team_name: "Arsenal",
    away_team_name: "Chelsea",
  });
  const result = mergeSupplementalMatchFeed([], [league, cup], { mode: "finished", scope: "all" });
  assert.equal(result.length, 2);
});
