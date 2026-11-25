import { createHash } from "node:crypto";
import { createTheSportsDbClient } from "./football-sources/thesportsdb.mjs";
import { createFootballDataOrgClient, normalizeFootballDataMatch } from "./football-sources/football-data-org.mjs";
import { createApiFootballClient, normalizeApiFootballFixture } from "./football-sources/api-football.mjs";
import { createWikipediaClient } from "./football-sources/wikipedia.mjs";
import { createOpenLigaDbClient, filterCurrentFootballLeagues } from "./football-sources/openligadb.mjs";
import { createOpenFootballClient, OPENFOOTBALL_SEASON_FILES } from "./football-sources/openfootball.mjs";
import { createWikidataClient, wikidataConstants } from "./football-sources/wikidata.mjs";
import { createCommonsClient } from "./football-sources/commons.mjs";
import { deriveProviderFacts } from "./football-sources/facts.mjs";
import { buildSyncBatches } from "./football-sources/batches.mjs";
import { normalizeName, scoreEntityMergeCandidate } from "./football-sources/normalize.mjs";

const SYNC_URL = "https://hwrsjmdcivdluztnhpxo.supabase.co/functions/v1/korax-world-data-sync";
const OIDC_AUDIENCE = "korax-world-data-sync";
const RUN_ID = String(process.env.GITHUB_RUN_ID || ("manual-" + Date.now()));
const RUN_ATTEMPT = String(process.env.GITHUB_RUN_ATTEMPT || "1");
const RUN_KEY = RUN_ID + ":" + RUN_ATTEMPT;
const NOW = new Date().toISOString();
const ALL_SOURCE_KEYS = ["thesportsdb", "openligadb", "openfootball", "wikidata", "wikimedia_commons", "football_data_org", "api_football", "wikipedia"];
const OPENLIGA_ONLY = process.env.KORAX_SYNC_SCOPE === "openligadb";
const SOURCE_KEYS = OPENLIGA_ONLY ? ["openligadb"] : ALL_SOURCE_KEYS;
const LEAGUES = [
  { country: "England", countryCode: "GB", league: "English Premier League" },
  { country: "Spain", countryCode: "ES", league: "Spanish La Liga" },
  { country: "Germany", countryCode: "DE", league: "German Bundesliga" },
  { country: "Italy", countryCode: "IT", league: "Italian Serie A" },
  { country: "France", countryCode: "FR", league: "French Ligue 1" },
  { country: "Saudi Arabia", countryCode: "SA", league: "Saudi Professional League" },
  { country: "Egypt", countryCode: "EG", league: "Egyptian Premier League" }
];

const data = {
  entities: new Map(),
  entitySources: new Map(),
  assets: new Map(),
  facts: new Map(),
  externalMatches: new Map(),
  coverage: new Map(),
  mergeCandidates: new Map(),
  sourceRuns: []
};
const sourceOutcomes = new Map();
const providerRecords = [];

function stableUuid(value) {
  const bytes = createHash("sha256").update(String(value)).digest().subarray(0, 16);
  bytes[6] = (bytes[6] & 15) | 80;
  bytes[8] = (bytes[8] & 63) | 128;
  const hex = bytes.toString("hex");
  return hex.slice(0, 8) + "-" + hex.slice(8, 12) + "-" + hex.slice(12, 16) + "-" + hex.slice(16, 20) + "-" + hex.slice(20);
}
function safeDate(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? date.toISOString().slice(0, 10) : null;
}
function add(map, value, key) {
  map.set(key || JSON.stringify(value), value);
}
function addCoverage(row) {
  const key = [row.source_key, row.entity_type, row.country_code, row.competition_name, row.season].join("|");
  add(data.coverage, row, key);
}
function report(sourceKey, status, requestCount, recordsSeen, extra) {
  const outcome = Object.assign({
    sourceKey, status, requestCount: requestCount || 0, recordsSeen: recordsSeen || 0
  }, extra || {});
  sourceOutcomes.set(sourceKey, outcome);
  console.log("[KORAX source] " + sourceKey + " status=" + status +
    " requests=" + outcome.requestCount + " records=" + outcome.recordsSeen +
    " errors=" + ((outcome.errors || []).length));
}
function addRecord(sourceKey, record) {
  if (!record || !record.externalId || !record.canonicalName || !record.entityType) return null;
  const externalId = String(record.externalId);
  const entityId = stableUuid(sourceKey + ":" + externalId);
  const entity = {
    id: entityId,
    entity_type: record.entityType,
    canonical_name: record.canonicalName,
    name_ar: record.nameAr || null,
    name_en: record.nameEn || null,
    aliases: Array.isArray(record.aliases) ? Array.from(new Set(record.aliases.filter(Boolean))).slice(0, 50) : [],
    country_code: record.countryCode || null,
    country_name: record.countryName || null,
    founded_on: safeDate(record.foundedOn),
    birth_date: safeDate(record.birthDate),
    metadata: record.metadata || {},
    completeness: Math.min(100, 20 + [
      record.nameAr, record.nameEn, record.countryName,
      record.foundedOn || record.birthDate, record.metadata && record.metadata.stadiumName,
      record.metadata && record.metadata.position, record.externalUrl
    ].filter(Boolean).length * 10),
    verification_status: "partial",
    is_active: true,
    last_seen_at: NOW
  };
  const previous = data.entities.get(entityId);
  if (previous) {
    entity.aliases = Array.from(new Set(previous.aliases.concat(entity.aliases))).slice(0, 50);
    entity.metadata = Object.assign({}, previous.metadata, entity.metadata);
    entity.name_ar = entity.name_ar || previous.name_ar;
    entity.name_en = entity.name_en || previous.name_en;
    entity.country_code = entity.country_code || previous.country_code;
    entity.country_name = entity.country_name || previous.country_name;
  }
  data.entities.set(entityId, entity);

  add(data.entitySources, {
    entity_id: entityId,
    source_key: sourceKey,
    external_id: externalId,
    external_url: record.externalUrl || null,
    match_method: sourceKey === "wikidata" ? "wikidata_id" : "provider_id",
    confidence: 1,
    is_primary: sourceKey === "wikidata",
    raw_payload: record.rawPayload || {},
    last_seen_at: NOW,
    updated_at: NOW
  }, sourceKey + ":" + externalId);

  for (const asset of record.assets || []) {
    if (!asset || !asset.assetUrl) continue;
    const row = {
      entity_id: entityId,
      asset_type: asset.assetType || "other",
      asset_url: asset.assetUrl,
      source_key: sourceKey,
      source_page_url: asset.sourcePageUrl || null,
      license_code: asset.licenseCode || null,
      license_url: asset.licenseUrl || null,
      creator: asset.creator || null,
      attribution_text: asset.attributionText || null,
      license_status: asset.licenseStatus || "unverified",
      is_primary: Boolean(asset.isPrimary),
      width: asset.width || null,
      height: asset.height || null,
      metadata: asset.metadata || {},
      last_checked_at: NOW
    };
    add(data.assets, row, entityId + ":" + row.asset_type + ":" + row.asset_url);
  }
  for (const fact of deriveProviderFacts(record).concat(record.facts || [])) {
    if (!fact || !fact.fieldKey || fact.value === undefined || fact.value === null) continue;
    const row = {
      entity_id: entityId,
      field_key: fact.fieldKey,
      value: fact.value,
      source_key: sourceKey,
      source_external_id: externalId,
      confidence: Math.max(0, Math.min(1, Number(fact.confidence === undefined ? 1 : fact.confidence))),
      observed_at: NOW,
      is_current: true,
      metadata: {}
    };
    add(data.facts, row, [entityId, row.field_key, sourceKey, externalId].join(":"));
  }
  providerRecords.push({
    entityId, sourceKey, entityType: record.entityType,
    externalId,
    name: record.canonicalName,
    countryCode: record.countryCode || null,
    wikidataId: record.metadata && record.metadata.wikidataId || null
  });
  return entityId;
}
function addExternalMatch(match) {
  if (!match || !match.sourceKey || !match.externalId || !match.fingerprint) return;
  const row = {
    source_key: match.sourceKey,
    external_id: String(match.externalId),
    competition_name: match.competitionName || null,
    season: match.season || null,
    match_date: safeDate(match.matchDate),
    kickoff_at: match.kickoffAt || null,
    home_team_name: match.homeTeamName,
    away_team_name: match.awayTeamName,
    home_score: Number.isInteger(match.homeScore) ? match.homeScore : null,
    away_score: Number.isInteger(match.awayScore) ? match.awayScore : null,
    status: match.status || null,
    is_live: Boolean(match.isLive),
    is_finished: Boolean(match.isFinished),
    fingerprint: match.fingerprint,
    match_confidence: Number.isFinite(match.matchConfidence) ? match.matchConfidence : null,
    raw_payload: match.rawPayload || {},
    last_seen_at: NOW,
    updated_at: NOW
  };
  add(data.externalMatches, row, row.source_key + ":" + row.external_id);
}
function addCommonsAsset(asset) {
  if (!asset || !asset.entityId || !asset.assetUrl) return;
  const row = {
    entity_id: asset.entityId,
    asset_type: asset.assetType,
    asset_url: asset.assetUrl,
    source_key: "wikimedia_commons",
    source_page_url: asset.sourcePageUrl,
    license_code: asset.licenseCode,
    license_url: asset.licenseUrl,
    creator: asset.creator,
    attribution_text: asset.attributionText,
    license_status: asset.licenseStatus,
    is_primary: false,
    width: asset.width,
    height: asset.height,
    metadata: asset.metadata || {},
    last_checked_at: NOW
  };
  add(data.assets, row, row.entity_id + ":" + row.asset_type + ":" + row.asset_url);
}
function linkTheSportsDbRecordsToWikidata() {
  const wikidataByQid = new Map();
  for (const row of data.entitySources.values()) {
    if (row.source_key === "wikidata" && /^Q\d+$/.test(row.external_id)) {
      wikidataByQid.set(row.external_id, row.entity_id);
    }
  }
  for (const item of providerRecords) {
    if (item.sourceKey !== "thesportsdb" || !item.wikidataId) continue;
    const qid = String(item.wikidataId).match(/Q\d+/);
    const canonicalId = qid ? wikidataByQid.get(qid[0]) : null;
    if (!canonicalId || canonicalId === item.entityId) continue;
    const oldEntity = data.entities.get(item.entityId);
    const canonical = data.entities.get(canonicalId);
    if (!canonical || !oldEntity) continue;
    canonical.name_ar = canonical.name_ar || oldEntity.name_ar;
    canonical.name_en = canonical.name_en || oldEntity.name_en;
    canonical.country_code = canonical.country_code || oldEntity.country_code;
    canonical.country_name = canonical.country_name || oldEntity.country_name;
    canonical.aliases = Array.from(new Set(canonical.aliases.concat(oldEntity.aliases))).slice(0, 50);
    canonical.metadata = Object.assign({}, oldEntity.metadata, canonical.metadata);
    canonical.completeness = Math.max(canonical.completeness, oldEntity.completeness);
    data.entities.set(canonicalId, canonical);
    data.entities.delete(item.entityId);
    const sourceRow = data.entitySources.get("thesportsdb:" + item.externalId);
    if (sourceRow) sourceRow.entity_id = canonicalId;
    const movedAssets = [];
    for (const [key, asset] of data.assets.entries()) {
      if (asset.entity_id === item.entityId) {
        data.assets.delete(key);
        asset.entity_id = canonicalId;
        movedAssets.push(asset);
      }
    }
    for (const asset of movedAssets) add(data.assets, asset, canonicalId + ":" + asset.asset_type + ":" + asset.asset_url);
    const movedFacts = [];
    for (const [key, fact] of data.facts.entries()) {
      if (fact.entity_id === item.entityId) {
        data.facts.delete(key);
        fact.entity_id = canonicalId;
        movedFacts.push(fact);
      }
    }
    for (const fact of movedFacts) add(data.facts, fact,
      [canonicalId, fact.field_key, fact.source_key, fact.source_external_id].join(":"));
    item.entityId = canonicalId;
  }
}

function addMergeCandidates() {
  const groups = new Map();
  for (const item of providerRecords) {
    // A source record can be moved to its Wikidata canonical entity during linking.
    // Ignore stale pre-link IDs so no merge candidate can reference a deleted row.
    if (!item.countryCode || !data.entities.has(item.entityId)) continue;
    const normalized = normalizeName(item.name);
    if (!normalized) continue;
    const key = item.entityType + "|" + item.countryCode + "|" + normalized;
    const list = groups.get(key) || [];
    list.push(item);
    groups.set(key, list);
  }
  for (const list of groups.values()) {
    for (let i = 0; i < list.length; i++) {
      for (let j = i + 1; j < list.length; j++) {
        if (list[i].sourceKey === list[j].sourceKey || list[i].entityId === list[j].entityId) continue;
        const left = list[i].entityId < list[j].entityId ? list[i] : list[j];
        const right = left === list[i] ? list[j] : list[i];
        const result = scoreEntityMergeCandidate(
          { entityType: left.entityType, name: left.name, countryCode: left.countryCode },
          { entityType: right.entityType, name: right.name, countryCode: right.countryCode }
        );
        if (result.score < 0.9) continue;
        const row = {
          left_entity_id: left.entityId,
          right_entity_id: right.entityId,
          confidence: result.score,
          reason: result.reason + "_requires_review",
          status: "pending"
        };
        add(data.mergeCandidates, row, row.left_entity_id + ":" + row.right_entity_id);
      }
    }
  }
}
async function oidcToken() {
  const requestUrl = process.env.ACTIONS_ID_TOKEN_REQUEST_URL;
  const requestToken = process.env.ACTIONS_ID_TOKEN_REQUEST_TOKEN;
  if (!requestUrl || !requestToken) throw new Error("GitHub Actions OIDC token endpoint is unavailable");
  const url = new URL(requestUrl);
  url.searchParams.set("audience", OIDC_AUDIENCE);
  const response = await fetch(url, { headers: { authorization: "Bearer " + requestToken } });
  if (!response.ok) throw new Error("GitHub OIDC request failed: HTTP " + response.status);
  const body = await response.json();
  if (!body || !body.value) throw new Error("GitHub OIDC response did not include a token");
  return body.value;
}
async function callSync(token, payload) {
  const response = await fetch(SYNC_URL, {
    method: "POST",
    headers: { authorization: "Bearer " + token, "content-type": "application/json" },
    body: JSON.stringify(payload),
    signal: AbortSignal.timeout(120000)
  });
  const body = await response.text();
  let parsed;
  try { parsed = body ? JSON.parse(body) : {}; } catch (_) { parsed = { raw: body.slice(0, 500) }; }
  if (!response.ok) throw new Error("World-data sync HTTP " + response.status + ": " + JSON.stringify(parsed).slice(0, 1200));
  return parsed;
}

async function persistInBatches(payload) {
  const totals = {};
  try {
    const batches = buildSyncBatches(payload);
    for (let index = 0; index < batches.length; index++) {
      const batch = batches[index];
      const table = Object.keys(batch)[0];
      // Provider collection can take longer than the five-minute GitHub OIDC token lifetime.
      // Mint a fresh, narrowly scoped token for every persistence batch.
      const batchToken = await oidcToken();
      const response = await callSync(batchToken, { action: "sync", ...batch });
      const countKey = ({
        entities: "entities",
        entitySources: "entitySources",
        assets: "assets",
        facts: "facts",
        externalMatches: "externalMatches",
        coverage: "coverage",
        mergeCandidates: "mergeCandidates",
        sourceRuns: "sourceRuns"
      })[table];
      totals[countKey] = (totals[countKey] || 0) + Number(response.counts?.[countKey] || 0);
      console.log("[korax-world-data-sync] persisted batch " + (index + 1) + "/" + batches.length +
        " (" + table + "): " + Number(response.counts?.[countKey] || 0));
    }
    return { counts: totals, batchCount: batches.length };
  } catch (error) {
    const failedRuns = (payload.sourceRuns || []).map(run => ({
      ...run,
      status: "failed",
      error_count: Number(run.error_count || 0) + 1,
      error_summary: ("Persistence batch failed: " + String(error)).slice(0, 1500),
      finished_at: new Date().toISOString()
    }));
    if (failedRuns.length) {
      const failureToken = await oidcToken();
      await callSync(failureToken, { action: "sync", sourceRuns: failedRuns }).catch(markError => {
        console.error("[korax-world-data-sync] could not record failed batch state:", markError);
      });
    }
    throw error;
  }
}
async function runTheSportsDb() {
  const source = createTheSportsDbClient({ apiKey: process.env.THESPORTSDB_API_KEY });
  const startedAt = new Date().toISOString();
  const errors = [];
  const records = [];
  let rateLimited = false;
  // Seven low-cost league metadata lookups plus three capped club-list lookups.
  // We intentionally avoid per-club squad fan-out on the shared free API.
  for (const item of LEAGUES) {
    if (rateLimited) break;
    try {
      records.push.apply(records, await source.searchLeagues(item.country, "Soccer"));
    } catch (error) {
      errors.push(item.country + " leagues: " + String(error).slice(0, 180));
      if (Number(error?.status) === 429) rateLimited = true;
    }
  }
  for (const item of LEAGUES.slice(0, 3)) {
    if (rateLimited) break;
    try {
      const teams = (await source.teamsInLeague(item.league)).slice(0, 10);
      records.push.apply(records, teams.map(team => Object.assign({}, team, {
        countryCode: item.countryCode, countryName: item.country
      })));
      addCoverage({
        source_key: "thesportsdb", entity_type: "club", country_code: item.countryCode,
        competition_name: item.league, season: "", coverage_status: teams.length ? "available" : "partial",
        record_count: teams.length, last_checked_at: NOW,
        metadata: { endpoint: "search_all_teams", freeTierLimit: 10, rightsReviewRequiredForImages: true }
      });
    } catch (error) {
      errors.push(item.league + ": " + String(error).slice(0, 180));
      if (Number(error?.status) === 429) rateLimited = true;
    }
  }
  for (const record of records) addRecord("thesportsdb", record);
  if (!records.length && !errors.length) errors.push("TheSportsDB endpoints responded but returned no usable metadata.");
  const stats = source.getStats();
  report("thesportsdb", errors.length ? (records.length ? "partial" : "failed") : "success",
    stats.requestCount, records.length, { errors, startedAt, rateLimited, squadsDeferred: true });
}
async function runOpenLigaDb(previousCoverage) {
  const source = createOpenLigaDbClient();
  const startedAt = new Date().toISOString();
  const errors = [];
  let checked = 0;
  let recordCount = 0;
  const sports = await source.availableSports();
  const soccerIds = sports.filter(item => /fußball|fussball|football|soccer/i.test(String(item.sportName || "")))
    .map(item => item.sportId).filter(value => value !== undefined && value !== null);
  let leagues;
  try { leagues = await source.availableLeagues(new Date().getUTCFullYear()); }
  catch (error) {
    errors.push("season-specific league list: " + String(error).slice(0, 160));
    leagues = await source.availableLeagues();
  }
  const filtered = filterCurrentFootballLeagues(leagues, {
    currentYear: new Date().getUTCFullYear(),
    minSeason: new Date().getUTCFullYear() - 1,
    footballSportIds: soccerIds.length ? soccerIds : [1, 79]
  });
  const latest = new Map();
  for (const league of filtered) {
    const old = latest.get(league.leagueShortcut);
    if (!old || Number(league.leagueSeason) > Number(old.leagueSeason)) latest.set(league.leagueShortcut, league);
  }
  const preferredOrder = [
    /^(1\.\s*)?(fußball-)?bundesliga/i,
    /premier league/i,
    /la liga|primera division/i,
    /^serie a/i,
    /^ligue 1/i,
    /saudi.*(professional|pro).*league/i,
    /champions league/i,
    /eredivisie/i,
    /primeira liga/i,
    /super lig/i,
    /egyptian premier league/i,
    /dfb.?pokal/i
  ];
  const list = Array.from(latest.values()).map(league => ({
    league,
    rank: preferredOrder.findIndex(pattern => pattern.test(String(league.leagueName || "")))
  })).filter(item => item.rank >= 0)
    .sort((a, b) => a.rank - b.rank || String(a.league.leagueName).localeCompare(String(b.league.leagueName)))
    .map(item => item.league).slice(0, 8);
  for (const league of list) {
    try {
      const previous = previousCoverage.find(row =>
        row.source_key === "openligadb" &&
        row.competition_name === String(league.leagueName) &&
        row.season === String(league.leagueSeason)
      );
      const lastChanged = previous && previous.metadata ? previous.metadata.lastChangedAt : null;
      const check = await source.checkLeague(league, lastChanged);
      checked++;
      recordCount += check.matches.length;
      if (check.league) addRecord("openligadb", check.league);
      for (const team of check.teams || []) addRecord("openligadb", team);
      for (const match of check.matches || []) addExternalMatch(match);
      addCoverage({
        source_key: "openligadb", entity_type: "competition", country_code: "",
        competition_name: String(league.leagueName), season: String(league.leagueSeason),
        coverage_status: "available",
        record_count: check.changed ? check.matches.length : Number(previous && previous.record_count || 0),
        last_checked_at: NOW,
        metadata: {
          shortcut: league.leagueShortcut,
          currentGroupOrderId: check.group && check.group.groupOrderID || null,
          lastChangedAt: check.lastChangedAt || lastChanged,
          changed: check.changed,
          license: "ODbL-1.0"
        }
      });
    } catch (error) { errors.push(league.leagueName + ": " + String(error).slice(0, 180)); }
  }
  const stats = source.getStats();
  const noCoverage = list.length === 0 || checked === 0;
  if (noCoverage && !errors.length) errors.push("No eligible football leagues were discovered or checked.");
  report("openligadb", errors.length ? (checked ? "partial" : "failed") : "success",
    stats.requestCount, recordCount, { errors, startedAt, leaguesChecked: checked, leaguesDiscovered: list.length });
}

async function runFootballDataOrg() {
  const token = String(process.env.FOOTBALL_DATA_API_TOKEN || "").trim();
  if (!token) {
    report("football_data_org", "skipped", 0, 0, {
      errors: [], reason: "FOOTBALL_DATA_API_TOKEN secret is not configured"
    });
    return;
  }
  const source = createFootballDataOrgClient({ token });
  const startedAt = new Date().toISOString();
  const errors = [];
  let records = 0;
  let competitionsChecked = 0;
  const now = new Date();
  const from = new Date(now.getTime() - 7 * 86400000).toISOString().slice(0, 10);
  const to = new Date(now.getTime() + 14 * 86400000).toISOString().slice(0, 10);

  try {
    const competitions = await source.competitions();
    for (const competition of competitions.slice(0, 12)) {
      const code = String(competition?.code || "").trim();
      const name = String(competition?.name || "").trim();
      if (!code || !name) continue;
      try {
        const matches = await source.matches(code, from, to);
        competitionsChecked++;
        for (const raw of matches) {
          const normalized = normalizeFootballDataMatch(raw);
          if (!normalized) continue;
          addExternalMatch(normalized);
          records++;
        }
        addCoverage({
          source_key: "football_data_org",
          entity_type: "competition",
          country_code: String(competition?.area?.code || ""),
          competition_name: name,
          season: String(competition?.currentSeason?.startDate || "").slice(0, 4),
          coverage_status: "available",
          record_count: matches.length,
          last_checked_at: NOW,
          metadata: {
            code,
            delayedScores: true,
            freeTierCompetitionLimit: 12,
            includesFixturesAndTables: true,
            attributionRequired: "Football data provided by the Football-Data.org API",
            logosAndProfilePhotosRequireSeparateRights: true
          }
        });
      } catch (error) {
        errors.push(name + ": " + String(error).slice(0, 180));
      }
    }
  } catch (error) {
    errors.push("competition catalogue: " + String(error).slice(0, 220));
  }

  const stats = source.getStats();
  report("football_data_org", errors.length ? (competitionsChecked ? "partial" : "failed") : "success",
    stats.requestCount, records, {
      errors, startedAt, competitionsChecked, dateFrom: from, dateTo: to,
      freeTierCompetitionLimit: 12, liveScores: false
    });
}

async function runApiFootball() {
  const apiKey = String(process.env.API_FOOTBALL_KEY || "").trim();
  const rightsConfirmed = String(process.env.API_FOOTBALL_RIGHTS_CONFIRMED || "").toLowerCase() === "true";
  if (!apiKey || !rightsConfirmed) {
    report("api_football", "skipped", 0, 0, {
      errors: [],
      reason: !apiKey
        ? "API_FOOTBALL_KEY secret is not configured"
        : "publication rights for the covered leagues/competitions have not been confirmed"
    });
    return;
  }

  const source = createApiFootballClient({ apiKey });
  const startedAt = new Date().toISOString();
  const errors = [];
  let records = 0;
  let leaguesChecked = 0;
  const season = new Date().getUTCFullYear();
  const requestedLimit = Number.parseInt(process.env.API_FOOTBALL_MAX_LEAGUES || "45", 10);
  const maxLeagues = Number.isFinite(requestedLimit) ? Math.max(1, Math.min(80, requestedLimit)) : 45;

  try {
    const catalogue = await source.leagues(season);
    const eligible = catalogue.filter(item => {
      const seasons = Array.isArray(item?.seasons) ? item.seasons : [];
      return seasons.some(value => Number(value?.year) === season);
    }).sort((a, b) => {
      const currentA = (a.seasons || []).some(value => Number(value?.year) === season && value?.current) ? 0 : 1;
      const currentB = (b.seasons || []).some(value => Number(value?.year) === season && value?.current) ? 0 : 1;
      return currentA - currentB || String(a?.league?.name || "").localeCompare(String(b?.league?.name || ""));
    });

    // Rotate the capped daily window so the 100-request free quota is not exhausted
    // on one fixed set of leagues. Only seasons explicitly reported by the provider are used.
    const dayNumber = Math.floor(Date.now() / 86400000);
    const start = eligible.length ? (dayNumber * maxLeagues) % eligible.length : 0;
    const rotated = eligible.slice(start).concat(eligible.slice(0, start));
    for (const item of rotated.slice(0, maxLeagues)) {
      const league = item?.league || {};
      const id = league?.id;
      const name = String(league?.name || "").trim();
      if (!id || !name) continue;
      try {
        const fixtures = await source.fixtures(id, season);
        leaguesChecked++;
        const kind = /cup|championship|trophy|champions league|europa league|conference league|world cup|nations league/i.test(name)
          ? "tournament" : "league";
        addRecord("api_football", {
          entityType: kind,
          externalId: String(id),
          canonicalName: name,
          nameEn: name,
          countryName: String(item?.country?.name || ""),
          metadata: { season, provider: "api_football", freePlanSeasonMayBeLimited: true }
        });
        for (const raw of fixtures) {
          const normalized = normalizeApiFootballFixture(raw);
          if (!normalized) continue;
          addExternalMatch(normalized);
          records++;
          const teams = raw?.teams || {};
          for (const side of ["home", "away"]) {
            const team = teams[side] || {};
            if (!team?.id || !team?.name) continue;
            addRecord("api_football", {
              entityType: "club",
              externalId: String(team.id),
              canonicalName: String(team.name),
              nameEn: String(team.name),
              countryName: String(item?.country?.name || ""),
              metadata: { leagueName: name, season, provider: "api_football" }
            });
          }
        }
        addCoverage({
          source_key: "api_football",
          entity_type: "competition",
          country_code: String(item?.country?.code || ""),
          competition_name: name,
          season: String(season),
          coverage_status: fixtures.length ? "available" : "partial",
          record_count: fixtures.length,
          last_checked_at: NOW,
          metadata: { leagueId: id, rotatedDailyWindow: true, freeRequestsPerDay: 100 }
        });
      } catch (error) {
        errors.push(name + ": " + String(error).slice(0, 180));
      }
    }
  } catch (error) {
    errors.push("league catalogue: " + String(error).slice(0, 220));
  }

  const stats = source.getStats();
  report("api_football", errors.length ? (leaguesChecked ? "partial" : "failed") : "success",
    stats.requestCount, records, {
      errors, startedAt, season, leaguesChecked, maxLeaguesPerRun: maxLeagues,
      rightsConfirmed, requestsRemainingInConfiguredBudget: stats.remaining
    });
}

async function runWikipedia() {
  const source = createWikipediaClient();
  const startedAt = new Date().toISOString();
  const errors = [];
  const selected = [];
  const seen = new Set();
  const eligibleTypes = new Set(["club", "national", "player", "coach", "league", "tournament"]);
  for (const item of providerRecords
    .filter(value => eligibleTypes.has(value.entityType) && data.entities.has(value.entityId))
    .sort((a, b) => Number(data.entities.get(b.entityId)?.completeness || 0) -
      Number(data.entities.get(a.entityId)?.completeness || 0))) {
    if (seen.has(item.entityId)) continue;
    seen.add(item.entityId);
    selected.push(item);
    if (selected.length >= 20) break;
  }

  if (!selected.length) {
    report("wikipedia", "skipped", 0, 0, {
      errors: [], reason: "No provider-backed football entities are currently available for exact-title enrichment"
    });
    return;
  }

  const counts = new Map();
  let imported = 0;
  for (const item of selected) {
    const entity = data.entities.get(item.entityId);
    if (!entity) continue;
    const arTitle = String(entity.name_ar || "").trim();
    const enTitle = String(entity.name_en || item.name || entity.canonical_name || "").trim();
    let page = null;
    try {
      if (arTitle) page = await source.pageSummary(arTitle, "ar");
      if (!page && enTitle && normalizeName(enTitle) !== normalizeName(arTitle)) {
        page = await source.pageSummary(enTitle, "en");
      } else if (!page && enTitle && !arTitle) {
        page = await source.pageSummary(enTitle, "en");
      }
    } catch (error) {
      errors.push(String(item.name || item.entityId) + ": " + String(error).slice(0, 180));
      continue;
    }
    if (!page) continue;

    const externalId = page.language + ":" + page.pageId + ":" + item.entityId;
    const sourceKey = "wikipedia:" + externalId;
    data.entitySources.set(sourceKey, {
      entity_id: item.entityId,
      source_key: "wikipedia",
      external_id: externalId,
      external_url: page.url,
      match_method: "exact_wikipedia_title",
      confidence: 0.94,
      is_primary: false,
      raw_payload: {
        title: page.title,
        pageId: page.pageId,
        language: page.language,
        license: page.license,
        attribution: "Wikipedia contributors — " + page.title
      },
      last_seen_at: NOW,
      updated_at: NOW
    });

    const fieldKey = "wikipedia_summary_" + page.language;
    data.facts.set([item.entityId, fieldKey, "wikipedia", externalId].join(":"), {
      entity_id: item.entityId,
      field_key: fieldKey,
      value: page.summary,
      source_key: "wikipedia",
      source_external_id: externalId,
      confidence: 0.94,
      observed_at: NOW,
      is_current: true,
      metadata: {
        source_url: page.url,
        license_code: page.license,
        license_url: "https://creativecommons.org/licenses/by-sa/4.0/",
        attribution_text: "Wikipedia contributors — " + page.title,
        language: page.language
      }
    });
    if (page.language === "ar" && !entity.name_ar) {
      entity.name_ar = page.title;
      data.entities.set(item.entityId, entity);
    }
    counts.set(item.entityType, (counts.get(item.entityType) || 0) + 1);
    imported++;
  }

  for (const [entityType, count] of counts.entries()) {
    addCoverage({
      source_key: "wikipedia",
      entity_type: entityType,
      country_code: "",
      competition_name: "",
      season: "",
      coverage_status: "available",
      record_count: count,
      last_checked_at: NOW,
      metadata: {
        method: "exact-title article match",
        maxEntitiesPerDailyRun: 20,
        license: "CC-BY-SA-4.0/GFDL",
        attributionRequired: true
      }
    });
  }

  const stats = source.getStats();
  report("wikipedia", errors.length ? (imported ? "partial" : "failed") : "success",
    stats.requestCount, imported, {
      errors, startedAt, candidates: selected.length,
      maxEntitiesPerDailyRun: 20,
      attributionRequired: true,
      license: "CC-BY-SA-4.0/GFDL"
    });
}

async function runOpenFootball() {
  const source = createOpenFootballClient();
  const startedAt = new Date().toISOString();
  const errors = [];
  let matches = 0;
  let filesSucceeded = 0;
  for (const file of OPENFOOTBALL_SEASON_FILES) {
    try {
      const result = await source.fetchSeasonFile(file);
      filesSucceeded++;
      matches += result.matches.length;
      for (const match of result.matches) addExternalMatch(match);
      addCoverage({
        source_key: "openfootball", entity_type: "competition", country_code: file.countryCode,
        competition_name: result.competitionName, season: file.season,
        coverage_status: "available", record_count: result.matches.length, last_checked_at: NOW,
        metadata: { file: file.path, license: "CC0-1.0", liveData: false }
      });
    } catch (error) { errors.push(file.path + ": " + String(error).slice(0, 180)); }
  }
  if (filesSucceeded === 0 || matches === 0) errors.push("No usable fixture rows were returned from the configured OpenFootball season files.");
  const stats = source.getStats();
  report("openfootball", errors.length ? (filesSucceeded ? "partial" : "failed") : "success",
    stats.requestCount, matches, { errors, startedAt, filesSucceeded, filesConfigured: OPENFOOTBALL_SEASON_FILES.length });
}
async function runWikidata() {
  const source = createWikidataClient();
  const startedAt = new Date().toISOString();
  const errors = [];
  const records = [];
  const countryQids = wikidataConstants.COUNTRY_QIDS;
  const groups = [];
  // Small country batches keep WDQS query plans bounded; every request is paced.
  for (let i = 0; i < countryQids.length; i += 4) groups.push(countryQids.slice(i, i + 4));
  let stopAfterError = false;
  for (const group of groups) {
    if (stopAfterError) break;
    try {
      records.push.apply(records, await source.fetchFootballEntities({ countryQids: group, limit: 60 }));
    } catch (error) {
      errors.push("club/competition metadata for countries " + group.join(",") + ": " + String(error).slice(0, 180));
      if (Number(error?.status) === 429 || /timeout|deadline|request budget/i.test(String(error))) stopAfterError = true;
    }
  }
  for (const group of groups) {
    if (stopAfterError) break;
    try {
      records.push.apply(records, await source.fetchFootballPeople({ countryQids: group, limit: 60 }));
    } catch (error) {
      errors.push("players/coaches for countries " + group.join(",") + ": " + String(error).slice(0, 180));
      if (Number(error?.status) === 429 || /timeout|deadline|request budget/i.test(String(error))) stopAfterError = true;
    }
  }
  for (const record of records) addRecord("wikidata", record);
  if (!records.length && !errors.length) errors.push("Wikidata responded without returning usable football entities or people.");
  for (const type of Array.from(new Set(records.map(item => item.entityType)))) {
    addCoverage({
      source_key: "wikidata", entity_type: type, country_code: "", competition_name: "", season: "",
      coverage_status: "available",
      record_count: records.filter(item => item.entityType === type).length,
      last_checked_at: NOW,
      metadata: { countryScope: countryQids.length + " curated country identifiers queried in " + groups.length + " batches", sourceLicense: "CC0-1.0" },
    });
  }
  const stats = source.getStats();
  report("wikidata", errors.length ? (records.length ? "partial" : "failed") : "success",
    stats.requestCount, records.length, {
      errors, startedAt, queryBatches: stats.requestCount,
      countryCount: countryQids.length,
      entityTypes: Array.from(new Set(records.map(item => item.entityType))),
      stoppedEarly: stopAfterError
    });
  return records;
}
async function runCommons(records) {
  const source = createCommonsClient();
  const startedAt = new Date().toISOString();
  const candidates = [];
  for (const record of records) {
    const entityId = stableUuid("wikidata:" + record.externalId);
    for (const asset of record.assetCandidates || []) {
      if (asset.fileName) candidates.push(Object.assign({}, asset, { entityId }));
    }
  }
  try {
    // Wikidata is not the only path to Commons: search the already imported,
    // provider-backed KORAX entity catalogue when Wikidata has no image links.
    const linkedEntityIds = new Set(candidates.map(item => item.entityId));
    const searchableEntities = Array.from(data.entities.values())
      .filter(entity =>
        ["club", "national", "player"].includes(entity.entity_type) &&
        !linkedEntityIds.has(entity.id)
      )
      .sort((a, b) => Number(b.completeness || 0) - Number(a.completeness || 0))
      .slice(0, 20);
    const searchedCandidates = await source.searchEntityFiles(searchableEntities);
    candidates.push(...searchedCandidates);

    const verified = candidates.length
      ? await source.verifyAssets(candidates.slice(0, 500))
      : [];
    for (const asset of verified) addCommonsAsset(asset);
    const stats = source.getStats();
    const displayable = verified.filter(asset => asset.licenseStatus === "verified").length;
    report("wikimedia_commons", stats.requestCount > 0 ? "success" : "partial",
      stats.requestCount, verified.length, {
        startedAt,
        candidates: candidates.length,
        searchedEntities: searchableEntities.length,
        displayableAssets: displayable,
        blockedAssets: verified.filter(asset => asset.licenseStatus === "blocked").length,
        logosPendingTrademarkReview: verified.filter(asset => asset.metadata && asset.metadata.trademarkReviewRequired).length,
        errors: stats.requestCount > 0 ? [] : ["No Commons API requests were completed."]
      });
    addCoverage({
      source_key: "wikimedia_commons", entity_type: "asset", country_code: "", competition_name: "", season: "",
      coverage_status: verified.length ? "available" : "unavailable",
      record_count: verified.length, last_checked_at: NOW,
      metadata: { candidates: candidates.length, searchedEntities: searchableEntities.length, verifiedForDisplay: displayable }
    });
  } catch (error) {
    report("wikimedia_commons", "failed", source.getStats().requestCount, 0,
      { startedAt, errors: [String(error).slice(0, 220)] });
  }
}
async function main() {
  const token = await oidcToken();
  const state = await callSync(token, { action: "state", sourceKeys: ["openligadb"] });
  const previousCoverage = Array.isArray(state.coverage) ? state.coverage : [];

  if (!OPENLIGA_ONLY) {
    if (String(process.env.THESPORTSDB_RIGHTS_CONFIRMED || "").toLowerCase() === "true" &&
        String(process.env.THESPORTSDB_API_KEY || "").trim()) {
      await runTheSportsDb();
    } else {
      report("thesportsdb", "skipped", 0, 0, {
        errors: [], reason: "Paid-tier key and permission for public-app distribution are required"
      });
    }
  }
  try { await runOpenLigaDb(previousCoverage); }
  catch (error) { report("openligadb", "failed", 0, 0, { errors: [String(error)] }); }
  if (!OPENLIGA_ONLY) {
    await runOpenFootball();
    await runFootballDataOrg();
    await runApiFootball();
    const wikidataRecords = await runWikidata();
    await runCommons(wikidataRecords);
    linkTheSportsDbRecordsToWikidata();
    await runWikipedia();
    addMergeCandidates();
  }

  for (const sourceKey of SOURCE_KEYS) {
    const item = sourceOutcomes.get(sourceKey) || {
      sourceKey, status: "skipped", requestCount: 0, recordsSeen: 0
    };
    data.sourceRuns.push({
      source_key: sourceKey,
      workflow_run_id: RUN_ID,
      run_key: RUN_KEY,
      status: item.status,
      started_at: item.startedAt || NOW,
      finished_at: new Date().toISOString(),
      request_count: item.requestCount || 0,
      records_seen: item.recordsSeen || 0,
      records_inserted: item.recordsSeen || 0,
      records_updated: 0,
      records_skipped: 0,
      error_count: (item.errors || []).length,
      error_summary: (item.errors || []).slice(0, 4).join(" | ").slice(0, 1500) || null,
      metadata: {
        status: item.status,
        details: Object.assign({}, item, { errors: (item.errors || []).slice(0, 8) })
      }
    });
  }

  const payload = {
    action: "sync",
    entities: Array.from(data.entities.values()),
    entitySources: Array.from(data.entitySources.values()),
    assets: Array.from(data.assets.values()),
    facts: Array.from(data.facts.values()),
    externalMatches: Array.from(data.externalMatches.values()),
    coverage: Array.from(data.coverage.values()),
    mergeCandidates: Array.from(data.mergeCandidates.values()).slice(0, 4000),
    sourceRuns: data.sourceRuns
  };
  const bytes = Buffer.byteLength(JSON.stringify(payload));
  const result = await persistInBatches(payload);

  const summary = Array.from(sourceOutcomes.values()).map(item => ({
    sourceKey: item.sourceKey, status: item.status, requests: item.requestCount, records: item.recordsSeen
  }));
  console.log(JSON.stringify({
    ok: true, runId: RUN_ID, runKey: RUN_KEY, serializedBytes: bytes,
    persistedBatchCount: result.batchCount, sourceOutcomes: summary, counts: result.counts
  }, null, 2));

  const problems = summary.filter(item => item.status === "failed" || item.status === "partial");
  if (problems.length) throw new Error("Provider coverage needs review: " +
    problems.map(item => item.sourceKey + "=" + item.status).join(", "));
}
main().catch(error => {
  console.error("[korax-world-data-sync] fatal:", error);
  process.exitCode = 1;
});
