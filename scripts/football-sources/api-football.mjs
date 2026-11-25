import { createJsonClient } from "./http.mjs";
import { createMatchFingerprint } from "./normalize.mjs";

const API = "https://v3.football.api-sports.io";

function validScore(value) {
  return Number.isInteger(value) && value >= 0 ? value : null;
}

export function normalizeApiFootballFixture(item) {
  const fixture = item?.fixture || {};
  const league = item?.league || {};
  const teams = item?.teams || {};
  const home = String(teams?.home?.name || "").trim();
  const away = String(teams?.away?.name || "").trim();
  const date = String(fixture?.date || "");
  const matchDate = date.slice(0, 10);
  const competitionName = String(league?.name || "").trim();
  const season = String(league?.season || "");
  if (!home || !away || !competitionName || !/^\d{4}-\d{2}-\d{2}$/.test(matchDate) || !season) return null;

  const status = String(fixture?.status?.short || "").toUpperCase();
  const isFinished = ["FT", "AET", "PEN"].includes(status);
  const isLive = ["1H", "HT", "2H", "ET", "BT", "P", "SUSP", "INT"].includes(status);
  const goals = item?.goals || {};
  const fingerprint = createMatchFingerprint({
    competitionName,
    season,
    round: league?.round || "",
    matchDate,
    homeTeam: home,
    awayTeam: away,
  });

  return {
    sourceKey: "api_football",
    externalId: String(fixture?.id || fingerprint.fingerprint),
    competitionName,
    season,
    matchDate,
    kickoffAt: date || null,
    homeTeamName: home,
    awayTeamName: away,
    homeScore: isFinished ? validScore(goals.home) : null,
    awayScore: isFinished ? validScore(goals.away) : null,
    status: status || "scheduled",
    isLive,
    isFinished,
    fingerprint: fingerprint.fingerprint,
    matchConfidence: null,
    rawPayload: {
      fixture: { id: fixture?.id || null, date, timezone: fixture?.timezone || null, status: fixture?.status || {} },
      league: { id: league?.id || null, name: competitionName, season, round: league?.round || null },
      teams: { home, away },
      goals,
    },
  };
}

export function createApiFootballClient({ apiKey, ...options } = {}) {
  if (!String(apiKey || "").trim()) throw new Error("API_FOOTBALL_KEY is required");
  const getJson = createJsonClient({
    source: "api_football",
    minIntervalMs: 6_500,
    maxRequests: 90,
    timeoutMs: 20_000,
    maxRetries: 0,
    defaultHeaders: {
      "x-apisports-key": String(apiKey).trim(),
      "User-Agent": "KORAX-World-Data-Sync/1.0",
    },
    ...options,
  });

  return {
    async leagues(season) {
      const payload = await getJson(API + "/leagues?season=" + encodeURIComponent(String(season)));
      return Array.isArray(payload?.response) ? payload.response : [];
    },
    async fixtures(leagueId, season) {
      const params = new URLSearchParams({ league: String(leagueId), season: String(season) });
      const payload = await getJson(API + "/fixtures?" + params.toString());
      return Array.isArray(payload?.response) ? payload.response : [];
    },
    getStats: () => getJson.getStats(),
  };
}
