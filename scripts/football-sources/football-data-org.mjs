import { createJsonClient } from "./http.mjs";
import { createMatchFingerprint } from "./normalize.mjs";

const API = "https://api.football-data.org/v4";

function score(value) {
  return Number.isInteger(value) && value >= 0 ? value : null;
}

export function normalizeFootballDataMatch(match) {
  const home = String(match?.homeTeam?.name || "").trim();
  const away = String(match?.awayTeam?.name || "").trim();
  const utcDate = String(match?.utcDate || "");
  const matchDate = utcDate.slice(0, 10);
  const competitionName = String(match?.competition?.name || "").trim();
  const season = String(match?.season?.startDate || match?.season?.id || "").slice(0, 4);
  if (!home || !away || !competitionName || !/^\d{4}-\d{2}-\d{2}$/.test(matchDate) || !season) return null;

  const finished = String(match?.status || "").toUpperCase() === "FINISHED";
  const fullTime = match?.score?.fullTime || {};
  const homeScore = finished ? score(fullTime.home) : null;
  const awayScore = finished ? score(fullTime.away) : null;
  const fingerprint = createMatchFingerprint({
    competitionName,
    season,
    round: match?.matchday ? "Matchday " + match.matchday : match?.stage || "",
    matchDate,
    homeTeam: home,
    awayTeam: away,
  });

  return {
    sourceKey: "football_data_org",
    externalId: String(match?.id || fingerprint.fingerprint),
    competitionName,
    season,
    matchDate,
    kickoffAt: utcDate || null,
    homeTeamName: home,
    awayTeamName: away,
    homeScore,
    awayScore,
    status: finished ? "finished" : "scheduled",
    // The free tier advertises delayed scores; never classify it as a live-score feed.
    isLive: false,
    isFinished: finished,
    fingerprint: fingerprint.fingerprint,
    matchConfidence: null,
    rawPayload: {
      status: match?.status || null,
      stage: match?.stage || null,
      matchday: match?.matchday ?? null,
      score: match?.score || null,
      venue: match?.venue || null,
      referee: match?.referees?.[0]?.name || null,
    },
  };
}

export function createFootballDataOrgClient({ token, ...options } = {}) {
  if (!String(token || "").trim()) throw new Error("FOOTBALL_DATA_API_TOKEN is required");
  const getJson = createJsonClient({
    source: "football_data_org",
    minIntervalMs: 6_500,
    maxRequests: 35,
    timeoutMs: 20_000,
    maxRetries: 0,
    defaultHeaders: { "X-Auth-Token": String(token).trim(), "User-Agent": "KORAX-World-Data-Sync/1.0" },
    ...options,
  });

  return {
    async competitions() {
      const payload = await getJson(API + "/competitions");
      return Array.isArray(payload?.competitions) ? payload.competitions : [];
    },
    async matches(code, dateFrom, dateTo) {
      const params = new URLSearchParams({ dateFrom, dateTo });
      const payload = await getJson(API + "/competitions/" + encodeURIComponent(code) + "/matches?" + params.toString());
      return Array.isArray(payload?.matches) ? payload.matches : [];
    },
    getStats: () => getJson.getStats(),
  };
}
