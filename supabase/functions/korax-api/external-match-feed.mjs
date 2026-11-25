const SOURCE_INFO = {
  openligadb: {
    name: "OpenLigaDB",
    license: "ODbL-1.0",
    url: "https://www.openligadb.de/",
  },
  openfootball: {
    name: "OpenFootball",
    license: "CC0-1.0",
    url: "https://github.com/openfootball/football.json",
  },
  football_data_org: {
    name: "Football-Data.org",
    license: "Free-tier API terms; attribution required",
    url: "https://www.football-data.org/",
  },
  api_football: {
    name: "API-Football",
    license: "API-Football terms; separate publication rights required",
    url: "https://www.api-football.com/terms",
  },
};

const MAIN_COMPETITIONS = new Set([
  "premier league",
  "english premier league",
  "la liga",
  "bundesliga",
  "german bundesliga",
  "serie a",
  "italian serie a",
  "ligue 1",
  "french ligue 1",
  "eredivisie",
  "primeira liga",
  "english championship",
  "brasileiro serie a",
]);

function normalizeText(value) {
  return String(value || "")
    .toLocaleLowerCase()
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .replace(/[’']/g, "")
    .replace(/[^\p{L}\p{N}]+/gu, " ")
    .trim()
    .replace(/\s+/g, " ");
}

function normalizeTeam(value) {
  return normalizeText(value)
    .replace(/\b(football club|athletic club|fc|afc|cf|sc|club)\b/g, " ")
    .trim()
    .replace(/\s+/g, " ");
}

function dateOf(value) {
  return String(value?.matchDate || value?.match_date || value?.startTime || value?.kickoff_at || "")
    .slice(0, 10);
}

function normalizeCompetition(value) {
  return normalizeText(value)
    .replace(/\b20\d{2}\s*[/-]\s*\d{2,4}\b/g, " ")
    .replace(/\b20\d{2}\b/g, " ")
    .replace(/\b(english|german|italian|french|spanish)\s+(premier league|bundesliga|serie a|ligue 1|la liga)\b/g, "$2")
    .replace(/\s+/g, " ")
    .trim();
}

function fixtureKey(value) {
  const date = dateOf(value);
  const competition = normalizeCompetition(value?.competitionName || value?.competition?.name || value?.competition_name || value?.league);
  const home = normalizeTeam(value?.home || value?.home_team || value?.home_team_name || value?.homeTeam?.name);
  const away = normalizeTeam(value?.away || value?.away_team || value?.away_team_name || value?.awayTeam?.name);
  return date && competition && home && away ? date + "|" + competition + "|" + home + "|" + away : "";
}

function isMainCompetition(value) {
  const normalized = normalizeText(value)
    .replace(/\b20\d{2}\s+\d{2,4}\b/g, " ")
    .replace(/\b20\d{2}\b/g, " ")
    .replace(/\s+/g, " ")
    .trim();
  return MAIN_COMPETITIONS.has(normalized);
}

function asScore(value) {
  if (value === null || value === undefined || value === "") return null;
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : null;
}

function mapSupplementalMatch(row) {
  const source = SOURCE_INFO[String(row?.source_key || "")];
  if (!source || row?.is_live === true) return null;

  const home = String(row?.home_team_name || "").trim();
  const away = String(row?.away_team_name || "").trim();
  const competitionName = String(row?.competition_name || "").trim();
  const matchDate = String(row?.match_date || "").slice(0, 10);
  if (!home || !away || !competitionName || !/^\d{4}-\d{2}-\d{2}$/.test(matchDate)) return null;

  const isFinished = row?.is_finished === true;
  const raw = row?.raw_payload && typeof row.raw_payload === "object" ? row.raw_payload : {};
  const rawTime = String(raw.time || "").trim();
  const kickoff = String(row?.kickoff_at || rawTime || matchDate);
  const externalId = String(row?.external_id || "");
  if (!externalId) return null;

  return {
    id: "supplemental-" + row.source_key + "-" + externalId.replace(/[^A-Za-z0-9_-]/g, "-").slice(0, 80),
    externalId,
    sourceKey: row.source_key,
    sourceName: source.name,
    sourceLicense: source.license,
    sourceUrl: source.url,
    competitionName,
    competition: { name: competitionName },
    home,
    away,
    homeTeam: { id: "", name: home, crest: "", logoUrl: "" },
    awayTeam: { id: "", name: away, crest: "", logoUrl: "" },
    homeScore: isFinished ? asScore(row?.home_score) : null,
    awayScore: isFinished ? asScore(row?.away_score) : null,
    status: isFinished ? "finished" : "scheduled",
    startTime: kickoff,
    matchDate,
    detailAvailable: false,
    events: [],
    lineups: [],
    statistics: [],
  };
}

export function mergeSupplementalMatchFeed(primaryMatches, supplementalRows, options = {}) {
  const primary = Array.isArray(primaryMatches) ? primaryMatches : [];
  const rows = Array.isArray(supplementalRows) ? supplementalRows : [];
  const mode = String(options.mode || "all");
  const scope = String(options.scope || "all");

  // Supplementary datasets are fixtures/results only. They must never create a live score.
  if (mode === "live") return primary;

  const primaryIds = new Set(primary.map(item => String(item?.id || "")).filter(Boolean));
  const seen = new Set(primary.map(fixtureKey).filter(Boolean));
  const supplemental = rows
    .filter(row => !row?.canonical_match_slug || !primaryIds.has(String(row.canonical_match_slug)))
    .filter(row => mode === "upcoming"
      ? row?.is_live !== true && row?.is_finished !== true
      : mode === "finished"
        ? row?.is_finished === true
        : row?.is_live !== true)
    .slice()
    .sort((a, b) => {
      const rank = value => value?.source_key === "openligadb" ? 0 : value?.source_key === "football_data_org" ? 1 : value?.source_key === "openfootball" ? 2 : 3;
      return rank(a) - rank(b);
    })
    .map(mapSupplementalMatch)
    .filter(Boolean)
    .filter(item => scope !== "main" || isMainCompetition(item.competitionName))
    .filter(item => {
      const key = fixtureKey(item);
      if (!key || seen.has(key)) return false;
      seen.add(key);
      return true;
    });

  return primary.concat(supplemental);
}

export const supplementalMatchInternals = {
  normalizeText,
  normalizeTeam,
  normalizeCompetition,
  fixtureKey,
  isMainCompetition,
  mapSupplementalMatch,
};
