import { createJsonClient } from "./http.mjs";

const API = "https://www.thesportsdb.com/api/v1/json/123";

function text(value) {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}
function integer(value) {
  const parsed = Number.parseInt(String(value ?? ""), 10);
  return Number.isFinite(parsed) ? parsed : null;
}
function safeAsset(url, assetType, sourcePageUrl) {
  const assetUrl = text(url);
  if (!assetUrl || !/^https:\/\//i.test(assetUrl)) return null;
  return {
    assetType,
    assetUrl,
    sourcePageUrl: sourcePageUrl || null,
    licenseCode: null,
    licenseUrl: null,
    creator: null,
    attributionText: null,
    licenseStatus: "unverified",
    isPrimary: false,
    metadata: { rightsReviewRequired: true, reason: "TheSportsDB image rights are not verified for this individual asset." },
  };
}

function teamRecord(team) {
  const id = text(team?.idTeam);
  const name = text(team?.strTeam);
  if (!id || !name) return null;
  const externalUrl = `https://www.thesportsdb.com/team/${encodeURIComponent(id)}`;
  const assets = [
    safeAsset(team.strTeamBadge, "logo", externalUrl),
    safeAsset(team.strTeamLogo, "logo", externalUrl),
    safeAsset(team.strTeamFanart1, "banner", externalUrl),
  ].filter(Boolean);
  return {
    entityType: /national team/i.test(String(team.strTeam || "")) ? "national" : "club",
    externalId: id,
    externalUrl,
    canonicalName: name,
    nameEn: name,
    aliases: [text(team.strTeamShort), text(team.strAlternate)].filter(Boolean),
    countryName: text(team.strCountry),
    metadata: {
      leagueName: text(team.strLeague),
      formedYear: integer(team.intFormedYear),
      stadiumName: text(team.strStadium),
      stadiumCapacity: integer(team.intStadiumCapacity),
      website: text(team.strWebsite),
      provider: "thesportsdb",
    },
    assets,
    rawPayload: {
      idTeam: id,
      strTeam: name,
      strTeamShort: text(team.strTeamShort),
      strAlternate: text(team.strAlternate),
      strCountry: text(team.strCountry),
      strLeague: text(team.strLeague),
      intFormedYear: integer(team.intFormedYear),
      strStadium: text(team.strStadium),
      intStadiumCapacity: integer(team.intStadiumCapacity),
      strWebsite: text(team.strWebsite),
    },
  };
}

function playerRecord(player) {
  const id = text(player?.idPlayer);
  const name = text(player?.strPlayer);
  if (!id || !name) return null;
  const externalUrl = `https://www.thesportsdb.com/player/${encodeURIComponent(id)}`;
  const portrait = safeAsset(player.strThumb, "portrait", externalUrl);
  return {
    entityType: "player",
    externalId: id,
    externalUrl,
    canonicalName: name,
    nameEn: name,
    aliases: [text(player.strPlayerAlternate)].filter(Boolean),
    countryName: text(player.strNationality),
    birthDate: text(player.dateBorn),
    metadata: {
      position: text(player.strPosition),
      height: text(player.strHeight),
      weight: text(player.strWeight),
      shirtNumber: text(player.strNumber),
      teamName: text(player.strTeam),
      provider: "thesportsdb",
    },
    assets: portrait ? [portrait] : [],
    rawPayload: {
      idPlayer: id,
      strPlayer: name,
      strPlayerAlternate: text(player.strPlayerAlternate),
      strNationality: text(player.strNationality),
      dateBorn: text(player.dateBorn),
      strPosition: text(player.strPosition),
      strHeight: text(player.strHeight),
      strWeight: text(player.strWeight),
      strNumber: text(player.strNumber),
      strTeam: text(player.strTeam),
    },
  };
}

function leagueRecord(league) {
  const id = text(league?.idLeague);
  const name = text(league?.strLeague);
  if (!id || !name) return null;
  const externalUrl = `https://www.thesportsdb.com/league/${encodeURIComponent(id)}`;
  const badge = safeAsset(league.strBadge, "logo", externalUrl);
  return {
    entityType: /cup|championship|trophy/i.test(name) ? "tournament" : "league",
    externalId: id,
    externalUrl,
    canonicalName: name,
    nameEn: name,
    aliases: [text(league.strLeagueAlternate)].filter(Boolean),
    countryName: text(league.strCountry),
    metadata: {
      sport: text(league.strSport),
      currentSeason: text(league.strCurrentSeason),
      formedYear: integer(league.intFormedYear),
      provider: "thesportsdb",
    },
    assets: badge ? [badge] : [],
    rawPayload: {
      idLeague: id,
      strLeague: name,
      strLeagueAlternate: text(league.strLeagueAlternate),
      strCountry: text(league.strCountry),
      strSport: text(league.strSport),
      strCurrentSeason: text(league.strCurrentSeason),
      intFormedYear: integer(league.intFormedYear),
    },
  };
}

export function createTheSportsDbClient(options = {}) {
  const getJson = createJsonClient({
    source: "thesportsdb",
    minIntervalMs: 2_500,
    maxRequests: 100,
    timeoutMs: 15_000,
    maxRetries: 1,
    defaultHeaders: { "user-agent": "KORAX-World-Data-Engine/1.0 (football metadata sync)" },
    ...options,
  });

  return {
    async searchTeams(name) {
      const payload = await getJson(`${API}/searchteams.php?t=${encodeURIComponent(name)}`);
      return (Array.isArray(payload?.teams) ? payload.teams : []).map(teamRecord).filter(Boolean);
    },
    async searchPlayers(name) {
      const payload = await getJson(`${API}/searchplayers.php?p=${encodeURIComponent(name)}`);
      return (Array.isArray(payload?.player) ? payload.player : []).map(playerRecord).filter(Boolean);
    },
    async searchLeagues(country, sport = "Soccer") {
      const payload = await getJson(`${API}/search_all_leagues.php?c=${encodeURIComponent(country)}&s=${encodeURIComponent(sport)}`);
      return (Array.isArray(payload?.countries) ? payload.countries : []).map(leagueRecord).filter(Boolean);
    },
    async teamsInLeague(leagueName) {
      const payload = await getJson(`${API}/search_all_teams.php?l=${encodeURIComponent(leagueName)}`);
      return (Array.isArray(payload?.teams) ? payload.teams : []).map(teamRecord).filter(Boolean);
    },
    async lookupTeam(id) {
      const payload = await getJson(`${API}/lookupteam.php?id=${encodeURIComponent(id)}`);
      return (Array.isArray(payload?.teams) ? payload.teams : []).map(teamRecord).filter(Boolean)[0] || null;
    },
    async lookupPlayer(id) {
      const payload = await getJson(`${API}/lookupplayer.php?id=${encodeURIComponent(id)}`);
      return (Array.isArray(payload?.players) ? payload.players : []).map(playerRecord).filter(Boolean)[0] || null;
    },
    getStats: () => getJson.getStats(),
  };
}

export const theSportsDbNormalizers = { teamRecord, playerRecord, leagueRecord, safeAsset };
