import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { isFootballNewsArticle } from "./news-filter.mjs";

/**
 * KOORA API edge adapter.
 *
 * Provider credentials stay in Cloudflare Worker secrets. Never place them in
 * the Android application. Responses are normalized to the Android client
 * contract documented in docs/API_CONTRACT.md.
 */
const SPORTSCORE = "https://sportscore.com";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") || "";
const SUPABASE_SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";

async function dbRows(path) {
  if (!SUPABASE_URL || !SUPABASE_SERVICE_KEY) throw new Error("Supabase database access is not configured");
  const response = await fetch(SUPABASE_URL + path, {
    headers: {
      apikey: SUPABASE_SERVICE_KEY,
      Authorization: "Bearer " + SUPABASE_SERVICE_KEY,
      Accept: "application/json",
    },
    signal: AbortSignal.timeout(12000),
  });
  const body = await response.text();
  if (!response.ok) throw new Error("KORAX cache HTTP " + response.status + ": " + body.slice(0, 300));
  return body ? JSON.parse(body) : [];
}


async function dbRpc(functionName, payload) {
  if (!SUPABASE_URL || !SUPABASE_SERVICE_KEY) throw new Error("Supabase database access is not configured");
  if (!/^[a-z0-9_]+$/.test(functionName)) throw new Error("Invalid database RPC name");
  const response = await fetch(SUPABASE_URL + "/rest/v1/rpc/" + functionName, {
    method: "POST",
    headers: {
      apikey: SUPABASE_SERVICE_KEY,
      Authorization: "Bearer " + SUPABASE_SERVICE_KEY,
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
    signal: AbortSignal.timeout(12000),
  });
  const body = await response.text();
  if (!response.ok) throw new Error("KORAX RPC HTTP " + response.status + ": " + body.slice(0, 500));
  return body ? JSON.parse(body) : [];
}

function cachedMatchPayload(row) {
  const detail = row?.detail ? unwrap(row.detail) : null;
  const raw = detail || row?.raw_fixture || {};
  const item = normalizeFixture(raw);
  item.id = String(row?.match_slug || item.id || "");
  item.status = String(row?.status_text || row?.status || item.status || "");
  item.startTime = String(row?.kickoff_at || item.startTime || "");
  item.home = String(row?.home_team || item.home || "");
  item.away = String(row?.away_team || item.away || "");
  item.homeTeam = {
    ...(item.homeTeam || {}),
    name: item.home,
    crest: row?.home_image || item.homeTeam?.crest || "",
    logoUrl: row?.home_image || item.homeTeam?.logoUrl || "",
  };
  item.awayTeam = {
    ...(item.awayTeam || {}),
    name: item.away,
    crest: row?.away_image || item.awayTeam?.crest || "",
    logoUrl: row?.away_image || item.awayTeam?.logoUrl || "",
  };
  item.homeScore = row?.home_score ?? item.homeScore;
  item.awayScore = row?.away_score ?? item.awayScore;
  item.competitionName = String(row?.competition_name || item.competitionName || "");
  item.competition = item.competition || {};
  item.competition.name = item.competitionName;
  if (row?.competition_image) item.competition.imageUrl = row.competition_image;
  item.events = Array.isArray(item.events) ? item.events : [];
  item.lineups = Array.isArray(item.lineups) ? item.lineups : [];
  item.statistics = Array.isArray(item.statistics) ? item.statistics : [];
  item.detailAvailable = Boolean(row?.detail);
  return item;
}


const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
  "x-content-type-options": "nosniff",
};

function json(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...JSON_HEADERS, ...headers },
  });
}

function corsHeaders(request, env) {
  const origin = request.headers.get("Origin") || "";
  const allowed = (env.ALLOWED_ORIGINS || "*")
    .split(",").map((value) => value.trim()).filter(Boolean);
  const permitted = allowed.includes("*") || allowed.includes(origin);
  return permitted ? {
    "access-control-allow-origin": allowed.includes("*") ? "*" : origin,
    "access-control-allow-methods": "GET, OPTIONS",
    "access-control-allow-headers": "Accept, Content-Type",
    "access-control-max-age": "86400",
    "vary": "Origin",
  } : {};
}

function unwrap(payload) {
  return payload && Object.prototype.hasOwnProperty.call(payload, "data")
    ? payload.data : payload;
}

function nestedName(value) {
  if (!value) return "";
  if (typeof value === "string") return value;
  if (typeof value === "object") return value.name || value.common_name || "";
  return "";
}

function participantLocation(participant) {
  return String(participant?.meta?.location || participant?.location || "").toLowerCase();
}

function normalizeParticipant(participant) {
  return {
    id: String(participant?.id ?? ""),
    name: participant?.name || "اسم غير متاح",
    crest: participant?.image_path || participant?.logoUrl || "",
    logoUrl: participant?.image_path || participant?.logoUrl || "",
    country: nestedName(participant?.country),
    venue: nestedName(participant?.venue),
  };
}

function normalizeScores(scores, homeId, awayId, stateName) {
  if (!Array.isArray(scores)) return { homeScore: null, awayScore: null };
  const finished = /finished|ft|full.?time|ended/i.test(String(stateName || ""));
  const preferred = finished
    ? ["CURRENT", "FT", "FULLTIME", "2ND_HALF"]
    : ["CURRENT", "1ST_HALF", "2ND_HALF", "EXTRA_TIME"];
  let chosen = null;
  for (const description of preferred) {
    chosen = scores.find((item) =>
      String(item?.description || "").toUpperCase() === description
    );
    if (chosen) break;
  }
  if (!chosen) chosen = scores[scores.length - 1];
  const home = scores.find((item) =>
    String(item?.participant_id ?? "") === String(homeId ?? "") &&
    String(item?.description || "").toUpperCase() === String(chosen?.description || "").toUpperCase()
  );
  const away = scores.find((item) =>
    String(item?.participant_id ?? "") === String(awayId ?? "") &&
    String(item?.description || "").toUpperCase() === String(chosen?.description || "").toUpperCase()
  );
  const read = (item) => item?.score?.goals ?? item?.score?.score ?? null;
  return {
    homeScore: read(home),
    awayScore: read(away),
    scoreType: chosen?.description || null,
  };
}

function httpsImage(...values) {
  for (const value of values) {
    if (typeof value === "string" && /^https:\/\//i.test(value)) return value;
  }
  return "";
}

function normalizeEvent(event) {
  const type = nestedName(event?.type) || event?.type_name || event?.name || "حدث";
  const playerRaw = event?.player && typeof event.player === "object" ? event.player : {};
  const player = nestedName(event?.player) || event?.player_name || "";
  const teamRaw = event?.participant || event?.team || {};
  const team = nestedName(teamRaw) || event?.team_name || "";
  const assistRaw = event?.assist || event?.assist_player || event?.assisted_by ||
    event?.assistPlayer || event?.assist_player_info || {};
  const assistName = nestedName(assistRaw) || event?.assist_player_name || event?.assist_name || "";
  const teamImage = httpsImage(teamRaw?.image_path, teamRaw?.image_url, teamRaw?.logo);
  const substitution = event?.is_sub || /substitution/i.test(String(type))
    ? [event?.player_in ? "دخول: " + event.player_in : "", event?.player_out ? "خروج: " + event.player_out : ""]
        .filter(Boolean).join(" • ")
    : "";
  return {
    minute: event?.minute ?? event?.time ?? event?.extra_minute ?? event?.sort_order ?? "",
    type_name: type,
    player_id: playerRaw?.id ?? event?.player_id ?? null,
    player_name: player,
    player_image_url: httpsImage(playerRaw?.image_path, playerRaw?.image_url, playerRaw?.photo_url),
    team_id: teamRaw?.id ?? event?.participant_id ?? event?.team_id ?? null,
    team: team ? { id: teamRaw?.id ?? null, name: team, imageUrl: teamImage } : null,
    team_side: event?.side || "",
    team_image_url: teamImage,
    assist_player_id: assistRaw?.id ?? event?.assist_player_id ?? null,
    assist_player_name: assistName,
    assist_player_image_url: httpsImage(assistRaw?.image_path, assistRaw?.image_url, assistRaw?.photo_url),
    detail: event?.addition || event?.info || event?.result || substitution || "",
    is_goal: Boolean(event?.is_goal),
  };
}

function normalizeLineup(item) {
  const player = item?.player || {};
  const team = item?.team || item?.participant || {};
  const teamName = nestedName(team) || item?.team_name || "";
  return {
    player_id: player?.id ?? item?.player_id ?? item?.id ?? null,
    player_name: nestedName(player) || item?.player_name || item?.name || "",
    imageUrl: httpsImage(player?.image_path, player?.image_url, item?.imageUrl, item?.image_url),
    position_name: nestedName(item?.position) || item?.position_name || "",
    shirt_number: item?.jersey_number ?? item?.shirt_number ?? item?.number ?? null,
    team_id: team?.id ?? item?.team_id ?? null,
    team: teamName ? { id: team?.id ?? null, name: teamName } : null,
    team_name: teamName,
    side: item?.side || "",
    is_substitute: Boolean(item?.is_substitute),
    captain: Boolean(item?.captain),
    rating: item?.rating ?? null,
    formation: item?.formation || "",
  };
}

function normalizeStatistic(item, teams = []) {
  const type = nestedName(item?.type) || item?.type_name || item?.name || item?.label || "";
  const homeValue = item?.home?.value ?? item?.home?.total ?? item?.home;
  const awayValue = item?.away?.value ?? item?.away?.total ?? item?.away;
  if (homeValue !== undefined || awayValue !== undefined) {
    const suffix = String(item?.suffix || "");
    const toText = (value) => value === null || value === undefined ? "" : String(value) + suffix;
    return [
      { name: type, value: toText(homeValue), team: teams[0]?.name || "", team_side: "home" },
      { name: type, value: toText(awayValue), team: teams[1]?.name || "", team_side: "away" },
    ].filter((row) => row.value !== "");
  }
  const value = item?.data?.value ?? item?.value ?? item?.statistic ?? "";
  const team = teams.find((candidate) =>
    String(candidate.id) === String(item?.participant_id ?? item?.team_id ?? "")
  );
  return [{
    name: type,
    value: value === null || value === undefined ? "" : String(value),
    team: team?.name || "",
    team_side: "",
  }];
}

function fixtureRoot(fixture) {
  if (fixture?.match && typeof fixture.match === "object") return fixture.match;
  if (fixture?.fixture && typeof fixture.fixture === "object") return fixture.fixture;
  return fixture || {};
}

function fixtureStatusText(fixture) {
  const root = fixtureRoot(fixture);
  const status = root.status || root.state || fixture?.status || fixture?.state || "";
  return String(root.status_text || nestedName(status) ||
    (typeof status === "string" ? status : status?.description || status?.short_name) || "");
}

function isLiveFixture(fixture) {
  return /live|in.?play|in_progress|1st.?half|2nd.?half|half.?time|\bht\b|\b1h\b|\b2h\b|extra.?time|penalt|الشوط|مباشر|استراحة|وقت إضافي|ركلات ترجيح/i
    .test(fixtureStatusText(fixture));
}

function normalizeFixture(fixture) {
  const root = fixtureRoot(fixture);
  const teams = root.teams || fixture?.teams || {};
  const participants = Array.isArray(root.participants) ? root.participants
    : Array.isArray(fixture?.participants) ? fixture.participants : [];
  const homeRaw = root.home_team || root.homeTeam ||
    (root.home && typeof root.home === "object" ? root.home : null) ||
    teams.home || participants.find((item) => participantLocation(item).includes("home")) || {};
  const awayRaw = root.away_team || root.awayTeam ||
    (root.away && typeof root.away === "object" ? root.away : null) ||
    teams.away || participants.find((item) => participantLocation(item).includes("away")) || {};
  const toTeam = (raw, fallbackName, logo) => {
    const obj = raw && typeof raw === "object" ? raw : {};
    const name = nestedName(raw) || (typeof raw === "string" ? raw : "") ||
      fallbackName || "اسم غير متاح";
    const image = obj.image_path || obj.logo || obj.logo_url || obj.image || obj.image_url || logo || "";
    const imageUrl = typeof image === "string" && /^https:\/\//i.test(image) ? image : "";
    return {
      id: String(obj.id ?? obj.slug ?? ""),
      name,
      crest: imageUrl,
      logoUrl: imageUrl,
      country: nestedName(obj.country),
    };
  };
  const home = toTeam(homeRaw,
    typeof root.home === "string" ? root.home : (root.home_name || root.home_team_name || ""),
    root.home_logo || root.home_image);
  const away = toTeam(awayRaw,
    typeof root.away === "string" ? root.away : (root.away_name || root.away_team_name || ""),
    root.away_logo || root.away_image);
  const score = root.score && typeof root.score === "object" ? root.score : {};
  const statusName = fixtureStatusText(fixture) || "الحالة غير متاحة";
  const nestedScores = normalizeScores(root.scores, home.id, away.id, statusName);
  const homeScore = firstScore(root.home_score, root.homeScore, score.home,
    score.home_score, nestedScores.homeScore);
  const awayScore = firstScore(root.away_score, root.awayScore, score.away,
    score.away_score, nestedScores.awayScore);
  const competition = root.competition || root.league || root.tournament ||
    root.competition_name || root.league_name || null;
  const competitionObject = typeof competition === "string" ? { name: competition }
    : competition && typeof competition === "object" ? { ...competition } : null;
  if (competitionObject) {
    competitionObject.imageUrl = httpsImage(
      competitionObject.image_path, competitionObject.image_url,
      competitionObject.logo, competitionObject.logo_url, competitionObject.image,
      root.competition_logo
    );
    competitionObject.logoUrl = competitionObject.imageUrl;
  }
  const eventsRaw = Array.isArray(root.events) ? root.events
    : Array.isArray(root.incidents) ? root.incidents
    : Array.isArray(root.timeline) ? root.timeline : [];
  const homeTeamName = home.name || "";
  const awayTeamName = away.name || "";
  const lineupObject = root.lineups && typeof root.lineups === "object" && !Array.isArray(root.lineups)
    ? root.lineups : null;
  let lineupRaw = Array.isArray(root.lineups) ? root.lineups
    : Array.isArray(root.lineup) ? root.lineup : [];
  if (lineupRaw.length === 0 && lineupObject) {
    const groups = [
      ["home_xi", "home", homeTeamName, false],
      ["away_xi", "away", awayTeamName, false],
      ["home_subs", "home", homeTeamName, true],
      ["away_subs", "away", awayTeamName, true],
    ];
    lineupRaw = groups.flatMap(([key, side, teamName, isSubstitute]) => {
      const players = Array.isArray(lineupObject[key]) ? lineupObject[key] : [];
      return players.map((player) => ({
        ...player,
        side,
        team_name: teamName,
        is_substitute: isSubstitute,
        formation: lineupObject[side + "_formation"] || "",
      }));
    });
  }
  const statisticRaw = Array.isArray(root.statistics) ? root.statistics
    : Array.isArray(root.stats) ? root.stats : [];
  const rawUrl = root.url || root.match_url || root.href ||
    fixture?.url || fixture?.match_url || fixture?.href || "";
  const urlSlug = String(rawUrl).match(/\/match\/[^/]+\/([A-Za-z0-9_-]+)\/?(?:[?#].*)?$/i)?.[1] ||
    String(rawUrl).match(/\/([A-Za-z0-9_-]{8,})\/?(?:[?#].*)?$/)?.[1] || "";
  const rawId = root.slug || root.match_slug || root.id || root.fixture_id ||
    root.match_id || root.fixture_slug || fixture?.slug || fixture?.match_slug ||
    fixture?.id || fixture?.fixture_id || fixture?.match_id || urlSlug;
  const rawTime = root.time || root.start_time || root.startTime || root.kickoff ||
    root.date || root.starting_at || root.start_at || "";
  return {
    id: String(rawId),
    name: [home.name, away.name].filter(Boolean).join(" × "),
    homeTeam: home, awayTeam: away, home: home.name, away: away.name,
    homeScore, awayScore, scoreType: nestedScores.scoreType || null, status: statusName,
    startTime: String(rawTime),
    competition: competitionObject,
    competitionName: nestedName(competition) || (typeof competition === "string" ? competition : ""),
    venue: root.venue || root.stadium || null,
    referee: root.referee || null,
    events: eventsRaw.map(normalizeEvent),
    lineups: lineupRaw.map(normalizeLineup).filter((item) => item.player_name),
    statistics: statisticRaw.flatMap((item) => normalizeStatistic(item, [home, away])),
    homeFormation: lineupObject?.home_formation || "",
    awayFormation: lineupObject?.away_formation || "",
    homeCoach: lineupObject?.home_coach || null,
    awayCoach: lineupObject?.away_coach || null,
    imageUrl: root.imageUrl || root.image_url || "",
  };
}

function firstScore(...values) {
  for (const value of values) {
    if (value === null || value === undefined || value === "") continue;
    const number = Number(value);
    if (Number.isFinite(number)) return number;
  }
  return null;
}

function normalizeTeam(team) {
  return {
    id: String(team?.id ?? ""),
    name: team?.name || "اسم غير متاح",
    imageUrl: team?.image_path || "",
    logoUrl: team?.image_path || "",
    country: nestedName(team?.country),
    stadium: nestedName(team?.venue),
    founded: team?.founded ?? null,
    players: Array.isArray(team?.players) ? team.players.map((entry) => {
      const player = entry?.player || entry || {};
      return {
        id: String(player?.id ?? entry?.player_id ?? ""),
        name: player?.name || entry?.player_name || "",
        imageUrl: player?.image_path || "",
        position: nestedName(player?.position) || nestedName(entry?.position),
        shirt_number: entry?.jersey_number ?? entry?.shirt_number ?? player?.shirt_number ?? null,
      };
    }).filter((player) => player.id && player.name) : [],
    coaches: Array.isArray(team?.coaches) ? team.coaches : [],
    trophies: Array.isArray(team?.trophies) ? team.trophies : [],
  };
}

function normalizePlayer(player) {
  const activeTeam = Array.isArray(player?.teams)
    ? player.teams.find((item) => item?.active) || player.teams[0]
    : null;
  return {
    id: String(player?.id ?? ""),
    name: player?.name || "اسم غير متاح",
    imageUrl: player?.image_path || "",
    position: nestedName(player?.position) || nestedName(player?.detailed_position),
    nationality: nestedName(player?.nationality) || nestedName(player?.country),
    date_of_birth: player?.date_of_birth || "",
    age: player?.age ?? null,
    height: player?.height ?? null,
    weight: player?.weight ?? null,
    shirt_number: player?.shirt_number ?? null,
    team: activeTeam?.team || activeTeam?.name || null,
    statistics: Array.isArray(player?.statistics) ? player.statistics : [],
    transfers: Array.isArray(player?.transfers) ? player.transfers : [],
  };
}

function normalizeTransfer(item) {
  const player = item?.player || {};
  const from = item?.fromTeam || item?.from_team || {};
  const to = item?.toTeam || item?.to_team || {};
  const league = item?.league || from?.league || to?.league || null;
  return {
    id: String(item?.id ?? ""),
    player: { id: String(player?.id ?? item?.player_id ?? ""), name: player?.name || item?.player_name || "اسم غير متاح", image_path: player?.image_path || "", imageUrl: player?.image_path || "" },
    playerName: player?.name || item?.player_name || "",
    fromTeam: { id: String(from?.id ?? item?.from_team_id ?? ""), name: from?.name || "", image_path: from?.image_path || "", imageUrl: from?.image_path || "" },
    toTeam: { id: String(to?.id ?? item?.to_team_id ?? ""), name: to?.name || "", image_path: to?.image_path || "", imageUrl: to?.image_path || "" },
    fromTeamName: from?.name || "", toTeamName: to?.name || "",
    type: item?.type || null, typeName: nestedName(item?.type) || item?.type_name || "",
    typeId: item?.type_id ?? null, date: item?.date || item?.completed_at || "",
    completed: Boolean(item?.completed), amount: item?.amount ?? null, league, leagueName: nestedName(league),
  };
}

function normalizeLeague(league) {
  return {
    id: String(league?.id ?? ""),
    name: league?.name || "اسم غير متاح",
    imageUrl: league?.image_path || "",
    logoUrl: league?.image_path || "",
    country: nestedName(league?.country),
    type: league?.type || "",
    currentSeason: league?.currentseason || league?.currentSeason || null,
  };
}

async function providerRequest(path, params = {}) {
  const url = new URL(path, SPORTSCORE);
  for (const [key, value] of Object.entries(params || {})) {
    if (value !== undefined && value !== null && value !== "") url.searchParams.set(key, String(value));
  }
  const response = await fetch(url, {
    headers: {
      accept: "application/json, text/plain, */*",
      "accept-language": "en-US,en;q=0.9,ar;q=0.7",
      "user-agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
      referer: "https://sportscore.com/",
      origin: "https://sportscore.com",
    },
    redirect: "follow",
    signal: AbortSignal.timeout(12000),
  });
  if (!response.ok) {
    const body = (await response.text().catch(() => "")).slice(0, 500);
    console.error("[korax-api] SportScore upstream HTTP", response.status, body);
    const error = new Error("SportScore returned HTTP " + response.status);
    error.status = response.status === 429 ? 503 : response.status;
    throw error;
  }
  return response.json();
}

const EDGE_CACHE = new Map();

function cacheRead(key) {
  const value = EDGE_CACHE.get(key);
  if (!value || value.expiresAt <= Date.now()) {
    EDGE_CACHE.delete(key);
    return null;
  }
  return value.payload;
}

function cacheWrite(key, payload, ttlSeconds) {
  EDGE_CACHE.set(key, { payload, expiresAt: Date.now() + ttlSeconds * 1000 });
  if (EDGE_CACHE.size > 500) {
    for (const [cacheKey, value] of EDGE_CACHE) {
      if (value.expiresAt <= Date.now()) EDGE_CACHE.delete(cacheKey);
    }
    while (EDGE_CACHE.size > 500) EDGE_CACHE.delete(EDGE_CACHE.keys().next().value);
  }
}


function normalizeSearchText(value) {
  return String(value || "")
    .toLocaleLowerCase()
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .replace(/[أإآ]/g, "ا")
    .replace(/ى/g, "ي")
    .replace(/ؤ/g, "و")
    .replace(/ئ/g, "ي")
    .replace(/ة/g, "ه")
    .replace(/[ـ]/g, " ")
    .replace(/[^\p{L}\p{N}]+/gu, " ")
    .trim()
    .replace(/\s+/g, " ");
}

function cachedEntitySlug(kind, name) {
  let hash = 2166136261;
  const normalized = normalizeSearchText(name);
  for (let index = 0; index < normalized.length; index += 1) {
    hash ^= normalized.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return "cached-" + kind + "-" + (hash >>> 0).toString(36);
}

async function getCachedEntityMatchRows() {
  const cacheKey = "entity-search:match-rows:v1";
  const cached = cacheRead(cacheKey);
  if (cached !== null) return cached;
  const select = "select=match_slug,match_date,kickoff_at,home_team,away_team,home_image,away_image,home_score,away_score,status,status_text,competition_name,competition_slug,competition_image,updated_at";
  const base = "/rest/v1/korax_sportscore_matches?" + select + "&order=updated_at.desc&limit=1000&offset=";
  const [first, second] = await Promise.all([
    dbRows(base + "0"),
    dbRows(base + "1000"),
  ]);
  const rows = [...(Array.isArray(first) ? first : []), ...(Array.isArray(second) ? second : [])];
  cacheWrite(cacheKey, rows, 180);
  return rows;
}

async function getCachedEntityDetailRows() {
  const cacheKey = "entity-search:detail-rows:v2";
  const cached = cacheRead(cacheKey);
  if (cached !== null) return cached;
  const path = "/rest/v1/korax_sportscore_matches?select=match_slug,match_date,kickoff_at,home_team,away_team,home_image,away_image,competition_name,competition_image,detail&detail=not.is.null&order=detail_updated_at.desc&limit=1000";
  const rows = await dbRows(path);
  const result = Array.isArray(rows) ? rows : [];
  cacheWrite(cacheKey, result, 180);
  return result;
}

function searchCachedTeamEntities(rows, query, limit) {
  const needle = normalizeSearchText(query);
  const found = new Map();
  for (const row of rows) {
    for (const side of ["home", "away"]) {
      const name = String(row?.[side + "_team"] || "").trim();
      if (!name || !normalizeSearchText(name).includes(needle)) continue;
      const key = normalizeSearchText(name);
      if (found.has(key)) continue;
      const imageUrl = httpsImage(row?.[side + "_image"]);
      const slug = cachedEntitySlug("team", name);
      const national = /national team|منتخب/i.test(name);
      found.set(key, {
        id: slug,
        slug,
        name,
        imageUrl,
        image_path: imageUrl,
        country: "",
        type: "team",
        koraxKind: national ? "national" : "club",
        source: "sportscore-supabase-cache",
      });
    }
  }
  return Array.from(found.values()).slice(0, limit);
}

function extractCachedLineupPlayers(matchRoot, row) {
  const lineup = matchRoot?.lineups;
  if (Array.isArray(lineup)) {
    return lineup.map((player) => ({
      ...player,
      side: player?.side || "",
      team_name: nestedName(player?.team) || player?.team_name || "",
      is_substitute: Boolean(player?.is_substitute),
    }));
  }
  if (!lineup || typeof lineup !== "object") return [];
  const groups = [
    ["home_xi", "home", row.home_team, false],
    ["away_xi", "away", row.away_team, false],
    ["home_subs", "home", row.home_team, true],
    ["away_subs", "away", row.away_team, true],
  ];
  return groups.flatMap(([key, side, teamName, substitute]) =>
    (Array.isArray(lineup[key]) ? lineup[key] : []).map((player) => ({
      ...player,
      side,
      team_name: teamName,
      is_substitute: substitute,
    }))
  );
}

async function getCachedPlayerIndex() {
  const cacheKey = "entity-search:players:v2";
  const cached = cacheRead(cacheKey);
  if (cached !== null) return cached;
  const rows = await getCachedEntityDetailRows();
  const found = new Map();
  for (const row of rows) {
    const root = row?.detail ? unwrap(row.detail) : null;
    const match = root?.match && typeof root.match === "object" ? root.match : root || {};
    const players = extractCachedLineupPlayers(match, row);
    const incidents = Array.isArray(match?.incidents) ? match.incidents
      : Array.isArray(match?.events) ? match.events
      : Array.isArray(match?.timeline) ? match.timeline : [];
    const addPlayer = (nameValue, details = {}, involvement = "lineup") => {
      const name = nestedName(nameValue) || (typeof nameValue === "string" ? nameValue : "") ||
        details?.player_name || details?.name || "";
      if (!name.trim()) return;
      const teamName = details?.team_name || nestedName(details?.team) || nestedName(details?.participant) ||
        (details?.side === "home" ? row.home_team : details?.side === "away" ? row.away_team : "");
      const key = normalizeSearchText(name) + "|" + normalizeSearchText(teamName);
      const current = found.get(key) || {
        id: cachedEntitySlug("player", name + "|" + teamName),
        slug: cachedEntitySlug("player", name + "|" + teamName),
        name,
        position: nestedName(details?.position) || details?.position_name || "",
        shirt_number: details?.number ?? details?.shirt_number ?? details?.jersey_number ?? null,
        team_name: teamName || "",
        imageUrl: httpsImage(details?.image_path, details?.image_url, details?.photo_url, details?.imageUrl),
        image_path: httpsImage(details?.image_path, details?.image_url, details?.photo_url, details?.imageUrl),
        matchesInCache: [],
        matches_with_lineup: 0,
        goals: 0,
        assists: 0,
        yellow_cards: 0,
        red_cards: 0,
        last_seen_match: row.match_slug || "",
        source: "sportscore-supabase-cache",
        koraxKind: "player",
        _appearanceMatches: new Set(),
        _eventKeys: new Set(),
      };
      if (row.match_slug && !current.matchesInCache.includes(row.match_slug)) {
        current.matchesInCache.push(row.match_slug);
      }
      if (!current.position) current.position = nestedName(details?.position) || details?.position_name || "";
      if (!current.imageUrl) {
        current.imageUrl = httpsImage(details?.image_path, details?.image_url, details?.photo_url, details?.imageUrl);
        current.image_path = current.imageUrl;
      }
      if (involvement === "lineup" && !details?.is_substitute && row.match_slug &&
          !current._appearanceMatches.has(row.match_slug)) {
        current._appearanceMatches.add(row.match_slug);
        current.matches_with_lineup += 1;
      }
      const event = details?._event;
      if (event && involvement !== "lineup") {
        const eventType = nestedName(event.type) || event.type_name || event.name || "";
        const eventDetail = String(event.addition || event.detail || event.info || "");
        const eventText = (String(eventType) + " " + eventDetail).toLowerCase();
        const eventKey = [
          row.match_slug, event.minute ?? event.time ?? "", eventType,
          nestedName(event.player) || event.player_name || "",
          nestedName(event.assist) || nestedName(event.assist_player) || event.assist_player_name || "",
        ].join("|");
        if (!current._eventKeys.has(eventKey)) {
          current._eventKeys.add(eventKey);
          if (involvement === "assist" && /goal|هدف/i.test(eventText) && !/cancel|disallow|إلغاء|ملغى/i.test(eventText)) {
            current.assists += 1;
          } else if (involvement === "event") {
            if (/goal|هدف/i.test(eventText) && !/cancel|disallow|own goal|إلغاء|ملغى/i.test(eventText)) current.goals += 1;
            if (/yellow card|second yellow|بطاقة صفراء|إنذار/i.test(eventText)) current.yellow_cards += 1;
            if (/red card|بطاقة حمراء|طرد/i.test(eventText)) current.red_cards += 1;
          }
        }
      }
      found.set(key, current);
    };
    for (const player of players) {
      addPlayer(player?.player || player?.name || player?.player_name, player, "lineup");
    }
    for (const event of incidents) {
      if (event?.player && typeof event.player === "object") {
        addPlayer(event.player, {
          ...event.player, player_name: nestedName(event.player),
          team: event?.team || event?.participant || event.player?.team,
          team_name: nestedName(event?.team) || nestedName(event?.participant) || "",
          side: event?.side || event?.team_side || "", _event: event,
        }, "event");
      } else {
        addPlayer(event?.player || event?.player_name, {
          player_name: event?.player || event?.player_name,
          team: event?.team || event?.participant,
          team_name: nestedName(event?.team) || nestedName(event?.participant) || "",
          side: event?.side || event?.team_side || "",
          image_path: event?.player_image_url || "", _event: event,
        }, "event");
      }
      const assist = event?.assist || event?.assist_player || event?.assist_player_name;
      if (assist) {
        addPlayer(assist, {
          player_name: nestedName(assist) || event?.assist_player_name || assist,
          team: event?.team || event?.participant,
          team_name: nestedName(event?.team) || nestedName(event?.participant) || "",
          side: event?.side || event?.team_side || "",
          image_path: event?.assist_player_image_url || "", _event: event,
        }, "assist");
      }
    }
  }
  const result = Array.from(found.values())
    .map(({ _appearanceMatches, _eventKeys, ...player }) => ({
      ...player, matches_in_cache: player.matchesInCache.length,
    }))
    .sort((a, b) => a.name.localeCompare(b.name));
  cacheWrite(cacheKey, result, 180);
  return result;
}

async function searchCachedPlayers(query, limit) {
  const needle = normalizeSearchText(query);
  const players = await getCachedPlayerIndex();
  return players.filter((player) =>
    normalizeSearchText(player.name).includes(needle) ||
    normalizeSearchText(player.team_name).includes(needle)
  ).slice(0, limit);
}

async function searchCachedCompetitions(query, limit) {
  const needle = normalizeSearchText(query);
  const [rows, stored] = await Promise.all([
    getCachedEntityMatchRows(),
    dbRows("/rest/v1/korax_sportscore_competitions?select=slug,standings&limit=100"),
  ]);
  const badges = new Map((Array.isArray(stored) ? stored : []).map((row) => [
    String(row.slug || ""),
    httpsImage(row.standings?.competition_logo, row.standings?.competition_image,
      row.standings?.imageUrl, row.standings?.logoUrl),
  ]));
  const found = new Map();
  for (const row of rows) {
    const providerName = String(row?.competition_name || "").trim();
    if (!providerName) continue;
    const key = normalizeSearchText(providerName);
    if (found.has(key)) continue;
    const known = KORAX_COMPETITIONS.find((item) =>
      normalizeSearchText(item.providerName || item.name) === key
    );
    const slug = known?.slug || cachedEntitySlug("competition", providerName);
    const imageUrl = httpsImage(badges.get(slug), row?.competition_image);
    const typeText = providerName.toLowerCase();
    const kind = /cup|champions|championship|world cup|confederation|trophy|tournament|كأس|ابطال|كأس العالم/i.test(providerName)
      ? "tournament" : "league";
    found.set(key, {
      id: known?.id || slug,
      slug,
      name: known?.name || providerName,
      providerName,
      country: known?.country || "",
      kind: known ? (known.slug === "uefa-champions-league" ? "tournament" : "league") : kind,
      koraxKind: known ? (known.slug === "uefa-champions-league" ? "tournament" : "league") : kind,
      imageUrl,
      logoUrl: imageUrl,
      source: "sportscore-supabase-cache",
    });
  }
  for (const known of KORAX_COMPETITIONS) {
    const key = normalizeSearchText(known.providerName || known.name);
    if (!found.has(key)) {
      const imageUrl = httpsImage(badges.get(known.slug));
      found.set(key, {
        id: known.id, slug: known.slug, name: known.name, providerName: known.providerName,
        country: known.country, kind: known.slug === "uefa-champions-league" ? "tournament" : "league",
        koraxKind: known.slug === "uefa-champions-league" ? "tournament" : "league", imageUrl, logoUrl: imageUrl, source: "sportscore-supabase-cache",
      });
    }
  }
  return Array.from(found.values()).filter((item) =>
    normalizeSearchText([item.name, item.providerName, item.country].join(" ")).includes(needle)
  ).slice(0, limit);
}


function canonicalEntityKind(type) {
  return ["club", "national", "player", "coach", "league", "tournament"].includes(type) ? type : "other";
}

function isDisplayableEntityAsset(asset) {
  const license = String(asset?.license_code || "");
  const allowedLicense = /cc0|public domain|cc[\s-]?by(?:[\\s-]?sa)?/i.test(license);
  const requiresAttribution = /cc[\s-]?by/i.test(license);
  const credit = String(asset?.attribution_text || asset?.creator || "").trim();
  return asset?.license_status === "verified" &&
    allowedLicense &&
    /^https:\\/\\//i.test(String(asset?.asset_url || "")) &&
    (!requiresAttribution || (credit && asset?.license_url && asset?.source_page_url)) &&
    !asset?.metadata?.trademarkReviewRequired;
}

async function searchCanonicalEntities(query, types, limit) {
  const normalizedQuery = String(query || "").trim();
  if (normalizedQuery.length < 2) return [];
  const rows = await dbRpc("search_korax_entities", {
    p_query: normalizedQuery,
    p_types: types,
    p_limit: Math.min(100, Math.max(1, Number(limit) || 30)),
  });
  const entities = Array.isArray(rows) ? rows : [];
  if (!entities.length) return [];
  const ids = entities.map(row => String(row.id || "")).filter(id => /^[0-9a-f-]{36}$/i.test(id));
  let assets = [];
  if (ids.length) {
    try {
      assets = await dbRows(
        "/rest/v1/korax_entity_assets?select=entity_id,asset_type,asset_url,is_primary,license_status,license_code,license_url,creator,attribution_text,source_page_url,metadata" +
        "&entity_id=in.(" + ids.map(encodeURIComponent).join(",") + ")" +
        "&license_status=eq.verified&order=is_primary.desc&limit=500"
      );
    } catch (error) {
      console.error("[korax-world-search] verified asset lookup failed", String(error).slice(0, 200));
    }
  }
  const imageByEntity = new Map();
  for (const asset of Array.isArray(assets) ? assets : []) {
    if (isDisplayableEntityAsset(asset) && !imageByEntity.has(asset.entity_id)) {
      imageByEntity.set(asset.entity_id, asset);
    }
  }
  return entities.map(row => {
    const type = canonicalEntityKind(row.entity_type);
    const name = String(row.name_ar || row.canonical_name || row.name_en || "").trim();
    const id = String(row.id || "");
    if (!name || !/^[0-9a-f-]{36}$/i.test(id)) return null;
    const imageAsset = imageByEntity.get(id) || null;
    const imageUrl = imageAsset?.asset_url || "";
    const competition = type === "league" || type === "tournament";
    return {
      id,
      slug: id,
      name,
      name_ar: row.name_ar || "",
      name_en: row.name_en || row.canonical_name || "",
      canonical_name: row.canonical_name || name,
      aliases: Array.isArray(row.aliases) ? row.aliases : [],
      country: row.country_name || row.country_code || "",
      country_code: row.country_code || "",
      imageUrl,
      image_path: imageUrl,
      logoUrl: imageUrl,
      imageAttribution: imageAsset?.attribution_text || imageAsset?.creator || "",
      imageLicense: imageAsset?.license_code || "",
      imageLicenseUrl: imageAsset?.license_url || "",
      imageSourceUrl: imageAsset?.source_page_url || "",
      type: type === "club" || type === "national" ? "team" : type,
      koraxKind: type,
      kind: competition ? type : undefined,
      source: "korax-canonical-entity-index",
      score: Number(row.score || 0),
      verification_status: row.verification_status || "unverified",
    };
  }).filter(Boolean);
}

async function getCanonicalEntityProfile(id) {
  const safeId = String(id || "");
  if (!/^[0-9a-f-]{36}$/i.test(safeId)) return null;
  const filterId = encodeURIComponent(safeId);
  const [entityRows, factRows, assetRows, sourceRows] = await Promise.all([
    dbRows("/rest/v1/korax_entities?select=*&id=eq." + filterId + "&is_active=eq.true&limit=1"),
    dbRows("/rest/v1/korax_entity_facts?select=field_key,value,source_key,confidence,observed_at&entity_id=eq." + filterId + "&is_current=eq.true&order=observed_at.desc&limit=100"),
    dbRows("/rest/v1/korax_entity_assets?select=asset_type,asset_url,source_key,source_page_url,license_code,license_url,creator,attribution_text,license_status,is_primary&entity_id=eq." + filterId + "&license_status=eq.verified&order=is_primary.desc&limit=30"),
    dbRows("/rest/v1/korax_entity_sources?select=source_key,external_id,external_url,confidence,is_primary&entity_id=eq." + filterId + "&limit=30"),
  ]);
  const entity = Array.isArray(entityRows) ? entityRows[0] : null;
  if (!entity) return null;
  const facts = {};
  for (const item of Array.isArray(factRows) ? factRows : []) {
    if (!item || !item.field_key || facts[item.field_key] !== undefined) continue;
    facts[item.field_key] = item.value;
  }
  const verifiedAssets = (Array.isArray(assetRows) ? assetRows : []).filter(isDisplayableEntityAsset);
  const preferredImage = verifiedAssets.find(asset => asset.is_primary) || verifiedAssets[0] || null;
  const metadata = entity.metadata && typeof entity.metadata === "object" ? entity.metadata : {};
  const root = {
    id: entity.id,
    slug: entity.id,
    name: entity.name_ar || entity.canonical_name || entity.name_en || "",
    name_ar: entity.name_ar || "",
    name_en: entity.name_en || entity.canonical_name || "",
    country: entity.country_name || entity.country_code || "",
    country_name: entity.country_name || "",
    country_code: entity.country_code || "",
    imageUrl: preferredImage?.asset_url || "",
    image_path: preferredImage?.asset_url || "",
    logoUrl: preferredImage?.asset_url || "",
    imageAttribution: preferredImage?.attribution_text || preferredImage?.creator || "",
    imageLicense: preferredImage?.license_code || "",
    imageLicenseUrl: preferredImage?.license_url || "",
    imageSourceUrl: preferredImage?.source_page_url || "",
    position: facts.playing_position || metadata.position || "",
    position_name: facts.playing_position || metadata.position || "",
    team_name: facts.current_team?.name || facts.current_team || metadata.teamName || "",
    date_of_birth: entity.birth_date || facts.birth_date || "",
    birthdate: entity.birth_date || facts.birth_date || "",
    founded: entity.founded_on || facts.founded_on || "",
    venue: metadata.stadiumName || facts.stadium?.name || "",
    capacity: metadata.stadiumCapacity || "",
    height: metadata.height || facts.height || "",
    weight: metadata.weight || facts.weight || "",
    facts,
    assets: verifiedAssets,
    sources: Array.isArray(sourceRows) ? sourceRows : [],
    source: "korax-canonical-entity-index",
    verification_status: entity.verification_status || "unverified",
    completeness: entity.completeness || 0,
  };
  for (const key of ["formedYear", "shirtNumber", "stadiumName", "leagueName", "officialWebsite"]) {
    if (metadata[key] !== undefined && metadata[key] !== null && metadata[key] !== "") {
      const mapped = key === "formedYear" ? "founded" : key === "shirtNumber" ? "shirt_number" :
        key === "stadiumName" ? "venue" : key === "leagueName" ? "league" : "website";
      root[mapped] = metadata[key];
    }
  }
  const type = canonicalEntityKind(entity.entity_type);
  if (type === "club" || type === "national") return { team: root };
  if (type === "player" || type === "coach") return { player: root };
  if (type === "league" || type === "tournament") return { competition: root };
  return { entity: root };
}

async function getCachedTeamProfile(slug) {
  const rows = await getCachedEntityMatchRows();
  const entries = [];
  for (const row of rows) {
    for (const side of ["home", "away"]) {
      const name = String(row?.[side + "_team"] || "").trim();
      if (name && cachedEntitySlug("team", name) === slug) entries.push({
        matchSlug: row.match_slug, competition: row.competition_name || "",
        kickoff_at: row.kickoff_at || "", status: row.status_text || row.status || "",
      });
    }
  }
  const row = rows.find((item) =>
    cachedEntitySlug("team", String(item.home_team || "")) === slug ||
    cachedEntitySlug("team", String(item.away_team || "")) === slug
  );
  if (!row) return null;
  const home = cachedEntitySlug("team", String(row.home_team || "")) === slug;
  const name = home ? row.home_team : row.away_team;
  const imageUrl = httpsImage(home ? row.home_image : row.away_image);
  const normalizedName = normalizeSearchText(name);
  const players = (await getCachedPlayerIndex())
    .filter((player) => normalizeSearchText(player.team_name) === normalizedName)
    .sort((a, b) => (b.matches_with_lineup || 0) - (a.matches_with_lineup || 0))
    .map((player) => ({
      id: player.id, slug: player.slug, name: player.name, imageUrl: player.imageUrl,
      position: player.position, shirt_number: player.shirt_number,
      matches_with_lineup: player.matches_with_lineup, goals: player.goals, assists: player.assists,
    }));
  return {
    team: {
      id: slug, slug, name, imageUrl, logoUrl: imageUrl, image_path: imageUrl,
      cached_matches: entries.length,
      competitions: [...new Set(entries.map((item) => item.competition).filter(Boolean))],
      players, roster_source: "players observed in cached SportScore lineups", roster_complete: false,
    },
  };
}

async function getCachedPlayerProfile(slug) {
  const players = await getCachedPlayerIndex();
  const player = players.find((item) => item.slug === slug);
  return player ? { player } : null;
}

async function getCachedCompetitionProfile(slug) {
  const rows = await getCachedEntityMatchRows();
  const entries = rows.filter((row) =>
    row?.competition_name && cachedEntitySlug("competition", row.competition_name) === slug
  );
  if (!entries.length) return null;
  const row = entries[0];
  return {
    competition: {
      id: slug, slug, name: row.competition_name,
      imageUrl: httpsImage(row.competition_image),
      logoUrl: httpsImage(row.competition_image),
      cached_matches: entries.length,
    },
  };
}

async function cachedProviderRequest(path, params, env, ttlSeconds) {
  const url = new URL(path, SPORTSCORE);
  for (const [key, value] of Object.entries(params || {})) {
    if (value !== undefined && value !== null && value !== "") url.searchParams.set(key, String(value));
  }
  const cacheKey = "provider:" + url.toString();
  const cached = cacheRead(cacheKey);
  if (cached !== null) return cached;
  const payload = await providerRequest(path, params);
  cacheWrite(cacheKey, payload, ttlSeconds);
  return payload;
}

function getList(payload) {
  const data = unwrap(payload);
  return Array.isArray(data) ? data : [];
}

function getOne(payload) {
  const data = unwrap(payload);
  return Array.isArray(data) ? data[0] || null : data || null;
}


// Multi-publisher RSS aggregation. No API key and no full-article republication.
const NEWS_FEEDS = [
  { id: "btolat", source: "بطولات", language: "ar", url: "https://www.btolat.com/rss/newsfeed" },
  { id: "btolat-egypt", source: "بطولات - الدوري المصري", language: "ar", url: "https://www.btolat.com/rss/newsfeed?leagueid=1193" },
  { id: "btolat-spain", source: "بطولات - الدوري الإسباني", language: "ar", url: "https://www.btolat.com/rss/newsfeed?leagueid=1399" },
  { id: "btolat-england", source: "بطولات - الدوري الإنجليزي", language: "ar", url: "https://www.btolat.com/rss/newsfeed?leagueid=1204" },
  { id: "btolat-ahly", source: "بطولات - الأهلي", language: "ar", url: "https://www.btolat.com/rss/newsfeed?teamid=8883" },
  { id: "btolat-zamalek", source: "بطولات - الزمالك", language: "ar", url: "https://www.btolat.com/rss/newsfeed?teamid=8959" },
  { id: "btolat-barcelona", source: "بطولات - برشلونة", language: "ar", url: "https://www.btolat.com/rss/newsfeed?teamid=15702" },
  { id: "btolat-real-madrid", source: "بطولات - ريال مدريد", language: "ar", url: "https://www.btolat.com/rss/newsfeed?teamid=16110" },
  { id: "btolat-liverpool", source: "بطولات - ليفربول", language: "ar", url: "https://www.btolat.com/rss/newsfeed?teamid=9249" },
  { id: "btolat-man-city", source: "بطولات - مانشستر سيتي", language: "ar", url: "https://www.btolat.com/rss/newsfeed?teamid=9259" },
  { id: "btolat-man-utd", source: "بطولات - مانشستر يونايتد", language: "ar", url: "https://www.btolat.com/rss/newsfeed?teamid=9260" },
  { id: "btolat-arsenal", source: "بطولات - آرسنال", language: "ar", url: "https://www.btolat.com/rss/newsfeed?teamid=9002" },
  { id: "btolat-juventus", source: "بطولات - يوفنتوس", language: "ar", url: "https://www.btolat.com/rss/newsfeed?teamid=11922" },
  { id: "btolat-psg", source: "بطولات - باريس سان جيرمان", language: "ar", url: "https://www.btolat.com/rss/newsfeed?teamid=10061" },
  { id: "btolat-pyramids", source: "بطولات - بيراميدز", language: "ar", url: "https://www.btolat.com/rss/newsfeed?teamid=23165" },
  { id: "hesport", source: "هسبورت", language: "ar", url: "https://www.hesport.com/feed" },
  { id: "kooora", source: "كووورة", language: "ar", url: "https://feeds.footballco.com/kooora/feed/6p5bsxot7te8yick" },
  { id: "bbc-football", source: "BBC Sport", language: "en", url: "https://feeds.bbci.co.uk/sport/football/rss.xml" },
  { id: "bbc-premier", source: "BBC Sport - Premier League", language: "en", url: "https://feeds.bbci.co.uk/sport/football/premier-league/rss.xml" },
  { id: "bbc-champions", source: "BBC Sport - Champions League", language: "en", url: "https://feeds.bbci.co.uk/sport/football/champions-league/rss.xml" },
  { id: "bbc-europe", source: "BBC Sport - European Football", language: "en", url: "https://feeds.bbci.co.uk/sport/football/european/rss.xml" },
  { id: "sky-football", source: "Sky Sports", language: "en", url: "https://www.skysports.com/rss/11095" },
  { id: "sky-premier", source: "Sky Sports - Premier League", language: "en", url: "https://www.skysports.com/rss/11661" },
  { id: "guardian-football", source: "The Guardian - Football", language: "en", url: "https://www.theguardian.com/football/rss" },
];

function decodeXml(value) {
  return String(value || "")
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
    .replace(/&#x([0-9a-f]+);/gi, (_, code) => {
      const point = parseInt(code, 16);
      return Number.isFinite(point) && point >= 0 && point <= 0x10ffff
        ? String.fromCodePoint(point) : "";
    })
    .replace(/&#([0-9]+);/g, (_, code) => {
      const point = Number(code);
      return Number.isFinite(point) && point >= 0 && point <= 0x10ffff
        ? String.fromCodePoint(point) : "";
    })
    .replace(/&nbsp;/gi, " ").replace(/&quot;/gi, '"').replace(/&apos;/gi, "'")
    .replace(/&lt;/gi, "<").replace(/&gt;/gi, ">").replace(/&amp;/gi, "&");
}

function decodeHtml(value) {
  return decodeXml(String(value || ""))
    .replace(/&#39;/gi, "'").replace(/&#34;/gi, '"');
}

function htmlText(value) {
  let text = String(value || "").replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1");
  // Publishers sometimes encode HTML twice (for example &amp;lt;p&amp;gt;).
  // Decode before stripping tags, and repeat once for double-encoded markup.
  for (let attempt = 0; attempt < 2; attempt += 1) {
    const decoded = decodeXml(text);
    if (decoded === text) break;
    text = decoded;
  }
  return text
    .replace(/<script\b[\s\S]*?<\/script>/gi, " ")
    .replace(/<style\b[\s\S]*?<\/style>/gi, " ")
    .replace(/<!--[\s\S]*?-->/g, " ")
    .replace(/<[^>]*>/g, " ")
    .replace(/\]\]>/g, " ")
    .replace(/\s+/gu, " ").trim();
}

function xmlField(block, name) {
  const re = new RegExp("<(?:[\\w.-]+:)?" + name + "\\b([^>]*)>([\\s\\S]*?)<\\/(?:[\\w.-]+:)?" + name + "\\s*>", "i");
  const match = String(block || "").match(re);
  return match ? { attrs: match[1] || "", value: match[2] || "" } : { attrs: "", value: "" };
}

function xmlAttribute(attributes, name) {
  const re = new RegExp("\\b" + name + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", "i");
  const match = String(attributes || "").match(re);
  return decodeHtml(match ? (match[1] ?? match[2] ?? match[3] ?? "") : "");
}

function safeNewsUrl(raw, base) {
  if (!raw) return "";
  try {
    const url = new URL(decodeHtml(raw).trim(), base);
    return url.protocol === "https:" ? url.toString() : "";
  } catch { return ""; }
}

function extractFeedImage(block, baseUrl) {
  const structuredTags = String(block || "").match(/<(?:[\w.-]+:)?(?:content|thumbnail|enclosure)\b[^>]*\/?\s*>/gi) || [];
  for (const tag of structuredTags) {
    const attributes = tag.slice(tag.indexOf(" ") + 1);
    const candidate = xmlAttribute(attributes, "url") || xmlAttribute(attributes, "href");
    if (!candidate) continue;
    const mime = xmlAttribute(attributes, "type").toLowerCase();
    const isImage = mime.startsWith("image/") ||
      /\.(?:png|jpe?g|webp|gif|avif)(?:[?#]|$)/i.test(candidate);
    if (!isImage) continue;
    const safe = safeNewsUrl(candidate, baseUrl);
    if (safe) return safe;
  }

  const imageTags = String(block || "").match(/<img\b[^>]*>/gi) || [];
  for (const tag of imageTags) {
    const attributes = tag.replace(/^<img\b/i, "").replace(/>$/, "");
    const candidate = xmlAttribute(attributes, "data-src") ||
      xmlAttribute(attributes, "data-original") ||
      xmlAttribute(attributes, "src") ||
      xmlAttribute(attributes, "srcset").split(",")[0].trim().split(/\s+/)[0];
    const safe = safeNewsUrl(candidate, baseUrl);
    if (safe) return safe;
  }

  const imageUrl = xmlField(block, "image").value;
  const nestedUrl = xmlField(imageUrl, "url").value;
  return safeNewsUrl(htmlText(nestedUrl), baseUrl);
}

function classifyNewsCategory(title, summary, feed = {}) {
  const text = (String(title || "") + " " + String(summary || "")).toLowerCase();

  if (/transfer|transfers|انتقال|ميركاتو|سوق الانتقالات|صفقة جديدة/i.test(text)) return "transfers";
  if (feed.id === "btolat-egypt") return "egypt";
  if (feed.id === "btolat-spain") return "la-liga";
  if (["btolat-egypt", "btolat-ahly", "btolat-zamalek", "btolat-pyramids"].includes(feed.id)) return "egypt";
  if (["btolat-spain", "btolat-barcelona", "btolat-real-madrid"].includes(feed.id)) return "la-liga";
  if (["btolat-england", "btolat-liverpool", "btolat-man-city", "btolat-man-utd", "btolat-arsenal", "bbc-premier", "sky-premier"].includes(feed.id)) return "premier-league";
  if (feed.id === "btolat-juventus") return "serie-a";
  if (feed.id === "btolat-psg") return "ligue-1";

  if (/la liga|الدوري الإسباني|ريال مدريد|برشلونة|فياريال|أتلتيكو مدريد|اتلتيكو مدريد|real madrid|barcelona|villarreal|atletico madrid/i.test(text)) {
    return "la-liga";
  }
  if (/premier league|الدوري الإنجليزي|ليفربول|مانشستر سيتي|مانشستر يونايتد|آرسنال|ارسنال|تشيلسي|tottenham|liverpool|manchester city|manchester united|arsenal|chelsea/i.test(text)) {
    return "premier-league";
  }
  if (/serie a|الدوري الإيطالي|إنتر ميلان|انتر ميلان|يوفنتوس|ميلان|نابولي|inter milan|juventus|ac milan|napoli/i.test(text)) {
    return "serie-a";
  }
  if (/bundesliga|الدوري الألماني|بايرن ميونخ|بوروسيا دورتموند|bayern munich|borussia dortmund/i.test(text)) {
    return "bundesliga";
  }
  if (/ligue 1|الدوري الفرنسي|باريس سان جيرمان|باريس سان|مارسيليا|موناكو|paris saint-germain|marseille/i.test(text)) {
    return "ligue-1";
  }
  if (/الدوري السعودي|السعودية|دوري روشن|saudi pro league|الهلال السعودي|النصر السعودي|الاتحاد السعودي|الأهلي السعودي|الاهلي السعودي|al.?hilal|al.?nassr|al.?ittihad|al.?ahli/i.test(text)) {
    return "saudi";
  }
  if (/الدوري المصري|كأس مصر|الأهلي المصري|الاهلي المصري|الزمالك|بيراميدز|منتخب مصر|egyptian premier league/i.test(text)) {
    return "egypt";
  }
  return "football";
}

function parseRssFeed(xml, feed) {
  const blocks = String(xml || "").match(/<(?:item|entry)\b[\s\S]*?<\/(?:item|entry)>/gi) || [];
  return blocks.slice(0, 60).map((block) => {
    const title = htmlText(xmlField(block, "title").value);
    const description = xmlField(block, "description").value ||
      xmlField(block, "summary").value || xmlField(block, "content").value ||
      xmlField(block, "encoded").value || "";
    const summary = htmlText(description).slice(0, 360);
    const link = xmlField(block, "link");
    const atomLink = block.match(/<link\b([^>]*)\/?\s*>/i);
    const rawLink = xmlAttribute(link.attrs || atomLink?.[1], "href") ||
      htmlText(link.value) || htmlText(xmlField(block, "guid").value);
    const url = safeNewsUrl(rawLink, feed.url);
    const rawDate = htmlText(xmlField(block, "pubDate").value) ||
      htmlText(xmlField(block, "published").value) ||
      htmlText(xmlField(block, "updated").value) ||
      htmlText(xmlField(block, "date").value);
    const timestamp = Date.parse(rawDate);
    const imageUrl = extractFeedImage(block, feed.url);
    if (!title || !url) return null;
    if (!isFootballNewsArticle(title, summary, feed.id)) return null;
    return {
      id: url, feedId: feed.id, title, summary, url, source: feed.source,
      publishedAt: Number.isFinite(timestamp) ? new Date(timestamp).toISOString() : rawDate,
      imageUrl: imageUrl || null, language: feed.language,
      category: classifyNewsCategory(title, summary, feed),
    };
  }).filter(Boolean);
}

async function fetchRssFeedUrl(url, feed) {
  const response = await fetch(url, {
    headers: {
      accept: "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8",
      "user-agent": "Mozilla/5.0 (compatible; KORAX-News/1.0; +https://github.com/shotalx2-lgtm/korax-)",
      "accept-language": "ar,en;q=0.8",
    },
    redirect: "follow",
    signal: AbortSignal.timeout(10000),
  });
  if (!response.ok) throw new Error(feed.id + " RSS HTTP " + response.status);
  return parseRssFeed(await response.text(), feed);
}

async function fetchRssFeed(feed) {
  try {
    const articles = await fetchRssFeedUrl(feed.url, feed);
    if (articles.length || !feed.fallbackUrl) return articles;
    throw new Error(feed.id + " RSS returned no parseable articles");
  } catch (primaryError) {
    if (!feed.fallbackUrl) throw primaryError;
    const fallbackArticles = await fetchRssFeedUrl(feed.fallbackUrl, { ...feed, url: feed.fallbackUrl });
    if (!fallbackArticles.length) throw new Error(feed.id + " primary and fallback RSS feeds returned no articles");
    return fallbackArticles;
  }
}

async function loadPublisherNews() {
  const responses = await Promise.allSettled(NEWS_FEEDS.map(fetchRssFeed));
  const articles = responses.flatMap((result) => result.status === "fulfilled" ? result.value : []);
  const unique = new Map();
  for (const article of articles) {
    const normalized = article.title.toLocaleLowerCase()
      .replace(/[^\p{L}\p{N}]+/gu, " ").trim();
    const key = article.url || normalized;
    if (normalized && !unique.has(key)) unique.set(key, article);
  }
  return {
    data: Array.from(unique.values()).sort((a, b) =>
      (Date.parse(b.publishedAt) || 0) - (Date.parse(a.publishedAt) || 0)).slice(0, 100),
    language: "ar+en",
    updatedAt: new Date().toISOString(),
    sources: NEWS_FEEDS.map((feed) => feed.source),
    feedsAvailable: responses.filter((result) => result.status === "fulfilled").length,
    feedsTotal: NEWS_FEEDS.length,
    feedErrors: responses.map((result, index) => result.status === "rejected" ? { feed: NEWS_FEEDS[index].id, error: String(result.reason?.message || result.reason) } : null).filter(Boolean),
  };
}

async function getCachedArabicNews() {
  const cacheKey = "news:football-only-rss-5";
  const cached = cacheRead(cacheKey);
  if (cached !== null) return cached;
  const payload = await loadPublisherNews();
  cacheWrite(cacheKey, payload, 300);
  return payload;
}

const KORAX_COMPETITIONS = [
  { id: "english-premier-league", slug: "english-premier-league", name: "الدوري الإنجليزي", country: "إنجلترا", providerName: "English Premier League" },
  { id: "spanish-la-liga", slug: "spanish-la-liga", name: "الدوري الإسباني", country: "إسبانيا", providerName: "Spanish La Liga" },
  { id: "french-ligue-1", slug: "french-ligue-1", name: "الدوري الفرنسي", country: "فرنسا", providerName: "French Ligue 1" },
  { id: "italian-serie-a", slug: "italian-serie-a", name: "الدوري الإيطالي", country: "إيطاليا", providerName: "Italian Serie A" },
  { id: "bundesliga", slug: "bundesliga", name: "الدوري الألماني", country: "ألمانيا", providerName: "Bundesliga" },
  { id: "saudi-professional-league", slug: "saudi-professional-league", name: "الدوري السعودي", country: "السعودية", providerName: "Saudi Professional League" },
  { id: "uefa-champions-league", slug: "uefa-champions-league", name: "دوري أبطال أوروبا", country: "أوروبا", providerName: "UEFA Champions League" },
];

const MAIN_COMPETITION_NAMES = [
  "English Premier League", "Premier League",
  "Spanish La Liga", "La Liga",
  "French Ligue 1", "Ligue 1",
  "Italian Serie A", "Serie A",
  "Bundesliga", "German Bundesliga",
  "Saudi Professional League", "Saudi Pro League",
  "UEFA Champions League",
  "Egyptian Premier League",
  "CAF Champions League", "CAF Confederation Cup",
  "AFC Champions League Elite",
  "UEFA Europa League", "UEFA Europa Conference League",
  "FIFA World Cup", "Africa Cup of Nations",
];


async function route(request, env) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "") || "/";
  const perPage = Math.min(50, Math.max(1, Number(url.searchParams.get("per_page") || 30)));

  if (path === "/health") return json({
    ok: true, service: "KORAX API", provider: "SportScore", providerConfigured: true,
    providerStatus: "github-sync-cache", apiKeyRequired: false, newsProvider: "publisher-rss",
  });

  if (path === "/api/news") {
    const payload = await getCachedArabicNews();
    const requested = Math.min(100, Math.max(1, Number(url.searchParams.get("per_page") || 40)));
    const language = url.searchParams.get("language");
    const category = url.searchParams.get("category");
    let data = payload.data;
    if (language === "ar" || language === "en") data = data.filter((item) => item.language === language);
    if (category) data = data.filter((item) => item.category === category);
    return json({ ...payload, data: data.slice(0, requested), source: "publisher-rss" });
  }

  if (path === "/api/matches") {
    const legacyLive = url.searchParams.get("live") === "1";
    const mode = url.searchParams.get("mode") || (legacyLive ? "live" : "all");
    const date = url.searchParams.get("date") || new Date().toISOString().slice(0, 10);
    const scope = url.searchParams.get("scope") || "all";
    if (!["upcoming", "live", "finished", "all"].includes(mode)) {
      return json({ error: "mode must be upcoming, live, finished, or all" }, 400);
    }
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return json({ error: "date must use YYYY-MM-DD" }, 400);
    const filters = ["select=*"];
    if (mode === "live") {
      filters.push("is_live=eq.true");
    } else {
      filters.push("match_date=eq." + encodeURIComponent(date));
      if (mode === "upcoming") filters.push("is_live=eq.false", "is_finished=eq.false");
      if (mode === "finished") filters.push("is_finished=eq.true");
    }
    if (scope === "main") {
      const allowed = MAIN_COMPETITION_NAMES
        .map((name) => "competition_name.eq." + encodeURIComponent(name))
        .join(",");
      filters.push("or=(" + allowed + ")");
    }
    filters.push("order=kickoff_at." + (mode === "finished" ? "desc" : "asc"));
    filters.push("limit=" + (mode === "live" ? "100" : "300"));
    const rows = await dbRows("/rest/v1/korax_sportscore_matches?" + filters.join("&"));
    const matches = (Array.isArray(rows) ? rows : []).map(cachedMatchPayload);
    return json({
      data: matches,
      meta: {
        count: matches.length, date, mode, scope,
        live: mode === "live",
        cacheUpdatedAt: rows.reduce((latest, row) =>
          String(row.updated_at || "") > latest ? String(row.updated_at) : latest, ""),
      },
      source: "sportscore-supabase-cache", updatedAt: new Date().toISOString(),
    });
  }

  const match = path.match(/^\/api\/matches\/([A-Za-z0-9_-]+)$/);
  if (match) {
    const slug = encodeURIComponent(match[1]);
    const rows = await dbRows("/rest/v1/korax_sportscore_matches?select=*&match_slug=eq." + slug + "&limit=1");
    const row = Array.isArray(rows) ? rows[0] : null;
    return row
      ? json({ data: cachedMatchPayload(row), source: "sportscore-supabase-cache" })
      : json({ error: "Match is not in the current SportScore cache yet", retryable: true }, 404);
  }

  if (path === "/api/competitions") {
    const search = (url.searchParams.get("search") || "").trim();
    const stored = await dbRows("/rest/v1/korax_sportscore_competitions?select=slug,standings&limit=100");
    const badges = new Map((Array.isArray(stored) ? stored : []).map((row) => [
      String(row.slug || ""),
      httpsImage(row.standings?.competition_logo, row.standings?.competition_image,
        row.standings?.imageUrl, row.standings?.logoUrl),
    ]));
    const index = KORAX_COMPETITIONS.map((item) => {
      const imageUrl = badges.get(item.slug) || "";
      return { ...item, kind: item.slug === "uefa-champions-league" ? "tournament" : "league", imageUrl, logoUrl: imageUrl };
    });
    if (search.length >= 2) {
      const [canonical, cached] = await Promise.all([
        searchCanonicalEntities(search, ["league", "tournament"], perPage),
        searchCachedCompetitions(search, perPage),
      ]);
      const merged = [...canonical, ...cached];
      const seen = new Set();
      const data = merged.filter(item => {
        const key = normalizeSearchText(item.name || item.providerName);
        if (!key || seen.has(key)) return false;
        seen.add(key);
        return true;
      }).slice(0, perPage);
      return json({
        data,
        source: canonical.length ? "korax-canonical-and-sportscore-search" : "sportscore-search",
        meta: { count: data.length, query: search, canonicalCount: canonical.length,
          catalogueScope: canonical.length ? "canonical provider catalog plus cached SportScore competitions" :
            "competitions recorded in the KORAX match cache" },
      });
    }
    return json({ data: index, source: "sportscore-competition-index" });
  }
  const competition = path.match(/^\/api\/competitions\/([A-Za-z0-9_-]+)$/);
  if (competition) {
    if (/^[0-9a-f-]{36}$/i.test(competition[1])) {
      const data = await getCanonicalEntityProfile(competition[1]);
      return data ? json({ data, source: "korax-canonical-entity-index" })
        : json({ error: "Competition is not in the canonical catalog", retryable: true }, 404);
    }
    const slug = encodeURIComponent(competition[1]);
    const rows = await dbRows("/rest/v1/korax_sportscore_competitions?select=slug,name,country,standings,updated_at&slug=eq." + slug + "&limit=1");
    const row = Array.isArray(rows) ? rows[0] : null;
    if (row) {
      return json({
        data: row.standings,
        name: row.name,
        country: row.country,
        updatedAt: row.updated_at,
        source: "sportscore-supabase-cache",
      });
    }
    if (competition[1].startsWith("cached-competition-")) {
      const data = await getCachedCompetitionProfile(competition[1]);
      return data
        ? json({ data, source: "sportscore-supabase-cache", meta: { partial: true, catalogueScope: "cached fixtures only" } })
        : json({ error: "Competition is not in the current KORAX cache", retryable: true }, 404);
    }
    return json({ error: "Standings are not in the current SportScore cache yet", retryable: true }, 503);
  }

  if (path === "/api/entities/search") {
    const search = (url.searchParams.get("search") || "").trim();
    if (search.length < 2) return json({ data: [], meta: { requiresSearch: true }, source: "korax-world-entity-index" });
    const types = ["club", "national", "player", "coach", "league", "tournament"];
    const [canonical, teamRows, playerRows, competitionRows] = await Promise.all([
      searchCanonicalEntities(search, types, perPage),
      getCachedEntityMatchRows().then(rows => searchCachedTeamEntities(rows, search, perPage)),
      searchCachedPlayers(search, perPage),
      searchCachedCompetitions(search, perPage),
    ]);
    const merged = [...canonical, ...teamRows, ...playerRows, ...competitionRows];
    const seen = new Set();
    const data = merged.filter(item => {
      const kind = item.koraxKind || item.kind || (item.type === "player" ? "player" : "club");
      const key = kind + "|" + normalizeSearchText(item.name);
      if (!normalizeSearchText(item.name) || seen.has(key)) return false;
      seen.add(key);
      return true;
    }).slice(0, perPage);
    return json({
      data,
      source: canonical.length ? "korax-canonical-and-sportscore-search" : "sportscore-search",
      meta: {
        count: data.length,
        query: search,
        canonicalCount: canonical.length,
        catalogueScope: canonical.length
          ? "canonical players, coaches, clubs, national teams and competitions plus cached SportScore data"
          : "current KORAX SportScore match, lineup and competition cache",
      },
    });
  }

  if (path === "/api/teams" || path === "/api/players") {
    const search = (url.searchParams.get("search") || "").trim();
    const isTeamSearch = path === "/api/teams";
    const requestedTypes = (url.searchParams.get("types") || "").split(",").map(value => value.trim()).filter(Boolean);
    if (search.length < 2) {
      return json({
        data: [],
        meta: { requiresSearch: true, catalogueScope: isTeamSearch
          ? "canonical clubs/national teams plus cached match participants"
          : "canonical players/coaches plus players in cached lineups" },
        source: "korax-canonical-entity-index",
      });
    }
    const types = isTeamSearch ? ["club", "national"] :
      requestedTypes.length ? requestedTypes.filter(value => ["player", "coach"].includes(value)) : ["player"];
    const canonicalPromise = searchCanonicalEntities(search, types, perPage);
    const cachedPromise = isTeamSearch
      ? getCachedEntityMatchRows().then(rows => searchCachedTeamEntities(rows, search, perPage))
      : (types.includes("player") ? searchCachedPlayers(search, perPage) : Promise.resolve([]));
    const [canonical, cached] = await Promise.all([canonicalPromise, cachedPromise]);
    const merged = [...canonical, ...cached];
    const seen = new Set();
    const data = merged.filter(item => {
      const kind = item.koraxKind || (isTeamSearch ? "club" : "player");
      const key = kind + "|" + normalizeSearchText(item.name);
      if (!normalizeSearchText(item.name) || seen.has(key)) return false;
      seen.add(key);
      return true;
    }).slice(0, perPage);
    return json({
      data,
      source: canonical.length ? "korax-canonical-and-sportscore-search" : "sportscore-search",
      meta: {
        count: data.length,
        query: search,
        canonicalCount: canonical.length,
        entityTypes: types,
        catalogueScope: canonical.length
          ? "canonical entities imported from active providers plus the current SportScore cache"
          : isTeamSearch
            ? "teams appearing in cached SportScore fixtures"
            : "players present in cached SportScore lineups, substitutes and incidents",
      },
    });
  }
  const team = path.match(/^\/api\/teams\/([A-Za-z0-9_-]+)$/);
  if (team) {
    if (/^[0-9a-f-]{36}$/i.test(team[1])) {
      const data = await getCanonicalEntityProfile(team[1]);
      return data ? json({ data, source: "korax-canonical-entity-index" })
        : json({ error: "Entity is not in the canonical catalog", retryable: true }, 404);
    }
    if (team[1].startsWith("cached-team-")) {
      const data = await getCachedTeamProfile(team[1]);
      return data
        ? json({ data, source: "sportscore-supabase-cache", meta: { partial: true, catalogueScope: "cached fixtures only" } })
        : json({ error: "Team is not in the current KORAX cache", retryable: true }, 404);
    }
    const payload = await cachedProviderRequest("/api/widget/team/", { sport: "football", slug: team[1], limit: 30 }, env, 300);
    return json({ data: unwrap(payload), source: "sportscore" });
  }

  const player = path.match(/^\/api\/players\/([A-Za-z0-9_-]+)$/);
  if (player) {
    if (/^[0-9a-f-]{36}$/i.test(player[1])) {
      const data = await getCanonicalEntityProfile(player[1]);
      return data ? json({ data, source: "korax-canonical-entity-index" })
        : json({ error: "Entity is not in the canonical catalog", retryable: true }, 404);
    }
    if (player[1].startsWith("cached-player-")) {
      const data = await getCachedPlayerProfile(player[1]);
      return data
        ? json({ data, source: "sportscore-supabase-cache", meta: { partial: true, catalogueScope: "players in cached match lineups and incidents" } })
        : json({ error: "Player is not in the current KORAX detail cache", retryable: true }, 404);
    }
    const payload = await cachedProviderRequest("/api/widget/player/", { sport: "football", slug: player[1] }, env, 300);
    return json({ data: unwrap(payload), source: "sportscore" });
  }

  if (path === "/api/transfers") {
    return json({
      data: [], source: "sportscore",
      meta: { available: false, reason: "provider_has_no_confirmed_transfers_endpoint" },
    });
  }

  return json({ error: "Not found" }, 404);
}

const EDGE_ENV = { ALLOWED_ORIGINS: Deno.env.get("ALLOWED_ORIGINS") || "*" };

Deno.serve(async (request) => {
  const cors = corsHeaders(request, EDGE_ENV);
  if (request.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: cors });
  }
  if (request.method !== "GET") {
    return json({ error: "Method not allowed" }, 405, cors);
  }

  try {
    const incoming = new URL(request.url);
    const routePrefixes = [
      "/functions/v1/korax-api",
      "/korax-api",
    ];
    for (const prefix of routePrefixes) {
      if (incoming.pathname === prefix) {
        incoming.pathname = "/";
        break;
      }
      if (incoming.pathname.startsWith(prefix + "/")) {
        incoming.pathname = incoming.pathname.slice(prefix.length);
        break;
      }
    }

    const routedRequest = new Request(incoming.toString(), request);
    const response = await route(routedRequest, EDGE_ENV);
    const headers = new Headers(response.headers);
    for (const [key, value] of Object.entries(cors)) headers.set(key, value);
    headers.set("x-korax-backend", "supabase-edge");
    return new Response(response.body, { status: response.status, headers });
  } catch (error) {
    const status = Number(error?.status || 502);
    return json({
      error: status === 503 ? "Football data service is unavailable or not configured" : "Unable to retrieve football data",
      retryable: status === 429 || status >= 500,
    }, status >= 400 && status <= 599 ? status : 502, cors);
  }
});

export { htmlText, classifyNewsCategory, parseRssFeed, normalizeEvent, normalizeFixture, isLiveFixture };
