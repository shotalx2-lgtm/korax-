import { createJsonClient } from "./http.mjs";
import { createMatchFingerprint } from "./normalize.mjs";

const RAW_BASE = "https://raw.githubusercontent.com/openfootball/football.json/master";

export const OPENFOOTBALL_SEASON_FILES = [
  { path: "2026-27/en.1.json", competitionName: "Premier League", season: "2026-27", countryCode: "GB" },
  { path: "2026-27/es.1.json", competitionName: "La Liga", season: "2026-27", countryCode: "ES" },
  { path: "2026-27/de.1.json", competitionName: "Bundesliga", season: "2026-27", countryCode: "DE" },
  { path: "2026-27/it.1.json", competitionName: "Serie A", season: "2026-27", countryCode: "IT" },
  { path: "2026-27/fr.1.json", competitionName: "Ligue 1", season: "2026-27", countryCode: "FR" },
  { path: "2026-27/nl.1.json", competitionName: "Eredivisie", season: "2026-27", countryCode: "NL" },
  { path: "2026-27/pt.1.json", competitionName: "Primeira Liga", season: "2026-27", countryCode: "PT" },
  { path: "2026-27/en.2.json", competitionName: "English Championship", season: "2026-27", countryCode: "GB" },
  { path: "2026/br.1.json", competitionName: "Brasileiro Série A", season: "2026", countryCode: "BR" },
];

function numericScore(value) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : null;
}

export function normalizeOpenFootballMatch(match, competition) {
  const homeTeam = typeof match?.team1 === "string" ? match.team1.trim() : "";
  const awayTeam = typeof match?.team2 === "string" ? match.team2.trim() : "";
  const matchDate = String(match?.date || "").slice(0, 10);
  if (!homeTeam || !awayTeam || !/^\d{4}-\d{2}-\d{2}$/.test(matchDate)) return null;

  const fullTime = Array.isArray(match?.score?.ft) ? match.score.ft : [];
  const homeScore = numericScore(fullTime[0]);
  const awayScore = numericScore(fullTime[1]);
  const isFinished = homeScore !== null && awayScore !== null;
  const round = typeof match?.round === "string" ? match.round.trim() : null;
  let kickoffAt = null;
  if (/T\d{2}:\d{2}/.test(String(match?.date || ""))) {
    const parsedKickoff = new Date(match.date);
    if (Number.isFinite(parsedKickoff.getTime())) kickoffAt = parsedKickoff.toISOString();
  }
  const fingerprint = createMatchFingerprint({
    competitionName: competition.competitionName,
    season: competition.season,
    round,
    matchDate,
    homeTeam,
    awayTeam,
  });

  return {
    sourceKey: "openfootball",
    externalId: fingerprint.fingerprint,
    competitionName: competition.competitionName,
    season: competition.season,
    matchDate,
    kickoffAt,
    homeTeamName: homeTeam,
    awayTeamName: awayTeam,
    homeScore,
    awayScore,
    status: isFinished ? "finished" : "scheduled",
    isLive: false,
    isFinished,
    fingerprint: fingerprint.fingerprint,
    matchConfidence: null,
    rawPayload: {
      round,
      date: match.date,
      time: match.time || null,
      team1: homeTeam,
      team2: awayTeam,
      score: match.score || null,
    },
    countryCode: competition.countryCode,
    identityStableAcrossReschedule: fingerprint.stableAcrossReschedule,
  };
}

export function parseOpenFootballSeason(payload, competition) {
  const matches = Array.isArray(payload?.matches) ? payload.matches : [];
  return matches.map(match => normalizeOpenFootballMatch(match, {
    ...competition,
    competitionName: payload?.name || competition.competitionName || "Unknown competition",
  })).filter(Boolean);
}

export function createOpenFootballClient(options = {}) {
  const getJson = createJsonClient({
    source: "openfootball",
    minIntervalMs: 1_000,
    maxRequests: 40,
    timeoutMs: 20_000,
    maxRetries: 2,
    defaultHeaders: { "user-agent": "KORAX-World-Data-Engine/1.0 (CC0 football fixtures sync)" },
    ...options,
  });

  return {
    async fetchSeasonFile(file) {
      const payload = await getJson(`${RAW_BASE}/${file.path}`);
      return {
        file,
        competitionName: payload?.name || file.competitionName,
        matches: parseOpenFootballSeason(payload, file),
      };
    },
    getStats: () => getJson.getStats(),
  };
}
