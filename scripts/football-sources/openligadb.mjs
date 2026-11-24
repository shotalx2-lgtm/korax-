import { createJsonClient } from "./http.mjs";
import { createMatchFingerprint } from "./normalize.mjs";

const API = "https://api.openligadb.de";
const POPULAR_FOOTBALL_NAME = /bundesliga|premier league|la liga|primera division|serie a|ligue 1|champions league|europa league|conference league|eredivisie|super lig|super league|botola|liga nos|primeira liga|world cup|weltmeisterschaft|nations league|dfb.?pokal|liga f|frauen.?bundesliga/i;
const EXCLUDED_NAME = /test|dummy|training|fan.?liga|simulation|comunio|debug|delete/i;

function text(value) {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}
function number(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}
function dateValue(value) {
  const parsed = Date.parse(String(value || ""));
  return Number.isFinite(parsed) ? new Date(parsed) : null;
}
function finalResult(match) {
  const results = Array.isArray(match?.matchResults) ? match.matchResults : [];
  return results.find(item => /final|endergebnis|after.?90|after.?extra|penalt/i.test(String(item?.resultName || item?.resultType?.resultName || "")))
    || results.slice().sort((a, b) => Number(b?.resultID || 0) - Number(a?.resultID || 0))[0]
    || null;
}
function leagueRecord(league) {
  const id = number(league?.leagueId);
  const name = text(league?.leagueName);
  if (!id || !name) return null;
  const sportId = number(league?.sport?.sportId);
  const isTournament = /cup|pokal|champions|europa|conference|world cup|weltmeisterschaft|nations league/i.test(name);
  return {
    entityType: isTournament ? "tournament" : "league",
    externalId: String(id),
    externalUrl: `https://api.openligadb.de/getmatchdata/${encodeURIComponent(league.leagueShortcut)}/${encodeURIComponent(league.leagueSeason)}`,
    canonicalName: name,
    nameEn: name,
    aliases: [],
    countryName: text(league?.leagueCountry),
    metadata: {
      leagueShortcut: text(league?.leagueShortcut),
      season: String(league?.leagueSeason || ""),
      sportId,
      sportName: text(league?.sport?.sportName),
      provider: "openligadb",
      license: "ODbL-1.0",
    },
    assets: [],
    rawPayload: {
      leagueId: id,
      leagueName: name,
      leagueShortcut: text(league?.leagueShortcut),
      leagueSeason: String(league?.leagueSeason || ""),
      sport: league?.sport || null,
    },
  };
}

function teamRecord(team, league) {
  const id = number(team?.teamId);
  const name = text(team?.teamName);
  if (!id || !name) return null;
  const page = `https://www.openligadb.de/teams/${id}`;
  const logo = text(team?.teamIconUrl);
  return {
    entityType: "club",
    externalId: String(id),
    externalUrl: page,
    canonicalName: name,
    nameEn: name,
    aliases: [text(team?.shortName)].filter(Boolean),
    countryName: text(league?.leagueCountry),
    metadata: { shortName: text(team?.shortName), leagueName: text(league?.leagueName), provider: "openligadb" },
    assets: logo && /^https:\/\//i.test(logo) ? [{
      assetType: "logo",
      assetUrl: logo,
      sourcePageUrl: page,
      licenseCode: "ODbL-1.0-data_asset_rights_unverified",
      licenseUrl: "https://www.openligadb.de/",
      creator: null,
      attributionText: "OpenLigaDB",
      licenseStatus: "unverified",
      isPrimary: false,
      metadata: { rightsReviewRequired: true },
    }] : [],
    rawPayload: { teamId: id, teamName: name, shortName: text(team?.shortName) },
  };
}

export function normalizeOpenLigaMatch(match, league) {
  const matchId = number(match?.matchID);
  const homeTeam = text(match?.team1?.teamName);
  const awayTeam = text(match?.team2?.teamName);
  const kickoff = dateValue(match?.matchDateTimeUTC || match?.matchDateTime);
  if (!matchId || !homeTeam || !awayTeam || !kickoff) return null;

  const result = finalResult(match);
  const homeScore = result ? number(result.pointsTeam1) : null;
  const awayScore = result ? number(result.pointsTeam2) : null;
  const isFinished = match?.matchFinished === true;
  const statusText = String(match?.matchStatus || "");
  const now = Date.now();
  const isLive = !isFinished && !/cancel|postpon|abandon/i.test(statusText) &&
    kickoff.getTime() <= now && kickoff.getTime() >= now - 4 * 60 * 60 * 1000;
  const groupName = text(match?.group?.groupName);
  const fingerprint = createMatchFingerprint({
    competitionName: league.leagueName,
    season: String(league.leagueSeason),
    round: groupName,
    matchDate: kickoff.toISOString().slice(0, 10),
    homeTeam,
    awayTeam,
  });

  return {
    sourceKey: "openligadb",
    externalId: String(matchId),
    competitionName: league.leagueName,
    season: String(league.leagueSeason),
    matchDate: kickoff.toISOString().slice(0, 10),
    kickoffAt: kickoff.toISOString(),
    homeTeamName: homeTeam,
    awayTeamName: awayTeam,
    homeScore,
    awayScore,
    status: isFinished ? "finished" : isLive ? "live" : statusText || "scheduled",
    isLive,
    isFinished,
    fingerprint: fingerprint.fingerprint,
    matchConfidence: null,
    rawPayload: {
      matchID: matchId,
      leagueId: match?.leagueId ?? league.leagueId,
      group: match?.group ? { groupName: groupName, groupOrderID: match.group.groupOrderID } : null,
      team1: { teamId: match?.team1?.teamId, teamName: homeTeam },
      team2: { teamId: match?.team2?.teamId, teamName: awayTeam },
      matchResults: Array.isArray(match?.matchResults) ? match.matchResults : [],
      goals: Array.isArray(match?.goals) ? match.goals : [],
      matchFinished: isFinished,
      matchStatus: statusText || null,
    },
  };
}

export function filterCurrentFootballLeagues(leagues, {
  currentYear = new Date().getUTCFullYear(),
  minSeason = currentYear - 1,
  allowedShortcuts = null,
} = {}) {
  const allowed = allowedShortcuts ? new Set(allowedShortcuts.map(value => String(value).toLowerCase())) : null;
  return (Array.isArray(leagues) ? leagues : []).filter(league => {
    const sportId = Number(league?.sport?.sportId);
    const season = Number(league?.leagueSeason);
    const name = String(league?.leagueName || "");
    const shortcut = String(league?.leagueShortcut || "");
    if (![1, 79].includes(sportId) || !Number.isFinite(season) || season < minSeason) return false;
    if (!shortcut || EXCLUDED_NAME.test(name) || !POPULAR_FOOTBALL_NAME.test(name)) return false;
    return !allowed || allowed.has(shortcut.toLowerCase());
  });
}

export function createOpenLigaDbClient(options = {}) {
  const getJson = createJsonClient({
    source: "openligadb",
    minIntervalMs: 1_000,
    maxRequests: 120,
    timeoutMs: 15_000,
    maxRetries: 1,
    defaultHeaders: { "user-agent": "KORAX-World-Data-Engine/1.0 (OpenLigaDB ODbL fixture sync)" },
    ...options,
  });
  const lastGroupPoll = new Map();

  return {
    async availableLeagues() {
      const payload = await getJson(`${API}/getavailableleagues`);
      return Array.isArray(payload) ? payload : [];
    },
    async currentGroup(shortcut) {
      return await getJson(`${API}/getcurrentgroup/${encodeURIComponent(shortcut)}`);
    },
    async lastChanged(shortcut, season, groupOrderId) {
      const key = `${shortcut}:${season}:${groupOrderId}`;
      const last = lastGroupPoll.get(key) || 0;
      const waitMs = 60_000 - (Date.now() - last);
      if (waitMs > 0) await new Promise(resolve => setTimeout(resolve, waitMs));
      lastGroupPoll.set(key, Date.now());
      return await getJson(`${API}/getlastchangedate/${encodeURIComponent(shortcut)}/${encodeURIComponent(season)}/${encodeURIComponent(groupOrderId)}`);
    },
    async matchesForGroup(shortcut, season, groupOrderId) {
      const payload = await getJson(`${API}/getmatchdata/${encodeURIComponent(shortcut)}/${encodeURIComponent(season)}/${encodeURIComponent(groupOrderId)}`);
      return Array.isArray(payload) ? payload : [];
    },
    async checkLeague(league, previousLastChangedAt = null) {
      const group = await this.currentGroup(league.leagueShortcut);
      const groupOrderId = number(group?.groupOrderID);
      if (!groupOrderId) return { changed: false, group, matches: [], lastChangedAt: null };
      const changedAt = await this.lastChanged(league.leagueShortcut, league.leagueSeason, groupOrderId);
      if (previousLastChangedAt && String(previousLastChangedAt) === String(changedAt)) {
        return { changed: false, group, matches: [], lastChangedAt: String(changedAt) };
      }
      const rawMatches = await this.matchesForGroup(league.leagueShortcut, league.leagueSeason, groupOrderId);
      return {
        changed: true,
        group,
        league: leagueRecord(league),
        lastChangedAt: String(changedAt),
        matches: rawMatches.map(match => normalizeOpenLigaMatch(match, league)).filter(Boolean),
        teams: rawMatches.flatMap(match => [
          teamRecord(match?.team1, league),
          teamRecord(match?.team2, league),
        ]).filter(Boolean),
      };
    },
    getStats: () => getJson.getStats(),
  };
}

export const openLigaDbNormalizers = { teamRecord, leagueRecord, finalResult };
