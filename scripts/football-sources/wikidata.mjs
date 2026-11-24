import { createJsonClient } from "./http.mjs";

// Migrate to WDQS v2 before the v1 service degradation/deprecation window.
export const WIKIDATA_SPARQL_ENDPOINT = "https://query-next.wikidata.org/sparql";
const COUNTRY_QIDS = [
  "Q145", "Q29", "Q183", "Q38", "Q142", "Q851", "Q79", "Q155",
  "Q414", "Q45", "Q55", "Q31", "Q43", "Q1028", "Q17", "Q30",
  "Q16", "Q96", "Q258", "Q1033", "Q1041", "Q117", "Q262", "Q948",
  "Q846", "Q878",
];
const CLASS_TYPES = {
  Q476028: "club",
  Q6979593: "national",
  Q15991303: "league",
  Q34262376: "tournament",
  Q1824674: "tournament",
  Q34262807: "tournament",
  Q34542757: "tournament",
  Q34542788: "tournament",
  Q34542827: "tournament",
  Q937857: "player",
  Q628099: "coach",
};

function bindingValue(binding, key) {
  return binding?.[key]?.value ?? null;
}
function qidFromUri(uri) {
  const match = String(uri || "").match(/\/(Q\d+)$/);
  return match?.[1] || null;
}
function commonsFileName(url) {
  const value = String(url || "");
  if (!value) return null;
  const part = value.split("/").pop()?.split("?")[0];
  if (!part) return null;
  try { return decodeURIComponent(part.replace(/_/g, " ")); } catch { return part; }
}
function toIsoDate(value) {
  const parsed = Date.parse(String(value || ""));
  return Number.isFinite(parsed) ? new Date(parsed).toISOString().slice(0, 10) : null;
}

export function normalizeWikidataBinding(binding) {
  const qid = qidFromUri(bindingValue(binding, "item"));
  const classQid = qidFromUri(bindingValue(binding, "class"));
  const entityType = CLASS_TYPES[classQid];
  const nameEn = bindingValue(binding, "nameEn") || bindingValue(binding, "itemLabel");
  const nameAr = bindingValue(binding, "nameAr");
  const canonicalName = nameEn || nameAr;
  if (!qid || !entityType || !canonicalName) return null;

  const countryQid = qidFromUri(bindingValue(binding, "country"));
  const stadiumQid = qidFromUri(bindingValue(binding, "stadium"));
  const leagueQid = qidFromUri(bindingValue(binding, "league"));
  const organizerQid = qidFromUri(bindingValue(binding, "organizer"));
  const imageUrl = bindingValue(binding, "image");
  const birthDate = toIsoDate(bindingValue(binding, "birthDate"));
  const teamQid = qidFromUri(bindingValue(binding, "team"));
  const position = bindingValue(binding, "positionLabel");
  const logoUrl = bindingValue(binding, "logo");
  const flagUrl = bindingValue(binding, "flag");
  const stadiumImageUrl = bindingValue(binding, "stadiumImage");
  const externalIds = {
    uefaTeamId: bindingValue(binding, "uefaTeamId"),
    transfermarktTeamId: bindingValue(binding, "transfermarktTeamId"),
    espnTeamId: bindingValue(binding, "espnTeamId"),
    scorebarId: bindingValue(binding, "scorebarId"),
    soccerbaseId: bindingValue(binding, "soccerbaseId"),
    footballDatabaseId: bindingValue(binding, "footballDatabaseId"),
    transfermarktCompetitionId: bindingValue(binding, "transfermarktCompetitionId"),
    fbrefCompetitionId: bindingValue(binding, "fbrefCompetitionId"),
    worldFootballId: bindingValue(binding, "worldFootballId"),
  };
  for (const [key, value] of Object.entries(externalIds)) if (!value) delete externalIds[key];

  const assetCandidates = [
    imageUrl ? { assetType: entityType === "player" ? "portrait" : "other", fileName: commonsFileName(imageUrl), specialFileUrl: imageUrl } : null,
    logoUrl ? { assetType: "logo", fileName: commonsFileName(logoUrl), specialFileUrl: logoUrl } : null,
    flagUrl ? { assetType: "flag", fileName: commonsFileName(flagUrl), specialFileUrl: flagUrl } : null,
    stadiumImageUrl ? { assetType: "stadium", fileName: commonsFileName(stadiumImageUrl), specialFileUrl: stadiumImageUrl } : null,
  ].filter(Boolean);

  const foundedOn = toIsoDate(bindingValue(binding, "inception"));
  const facts = [];
  if (foundedOn) facts.push({ fieldKey: "founded_on", value: foundedOn, confidence: 1 });
  if (birthDate) facts.push({ fieldKey: "birth_date", value: birthDate, confidence: 1 });
  if (teamQid) facts.push({ fieldKey: "current_team", value: { qid: teamQid, name: bindingValue(binding, "teamLabel") }, confidence: 0.9 });
  if (position) facts.push({ fieldKey: "playing_position", value: position, confidence: 1 });
  if (stadiumQid) facts.push({ fieldKey: "stadium", value: { qid: stadiumQid, name: bindingValue(binding, "stadiumLabel") }, confidence: 1 });
  if (leagueQid) facts.push({ fieldKey: "league", value: { qid: leagueQid, name: bindingValue(binding, "leagueLabel") }, confidence: 1 });
  if (organizerQid) facts.push({ fieldKey: "organizer", value: { qid: organizerQid, name: bindingValue(binding, "organizerLabel") }, confidence: 1 });

  return {
    entityType,
    externalId: qid,
    externalUrl: `https://www.wikidata.org/entity/${qid}`,
    canonicalName,
    nameAr,
    nameEn,
    aliases: [bindingValue(binding, "itemAltLabel")].filter(Boolean),
    countryCode: bindingValue(binding, "countryCode"),
    countryName: bindingValue(binding, "countryLabel"),
    foundedOn,
    birthDate,
    metadata: {
      countryQid,
      teamQid,
      position,
      height: bindingValue(binding, "height"),
      weight: bindingValue(binding, "weight"),
      stadiumQid,
      stadiumName: bindingValue(binding, "stadiumLabel"),
      stadiumCapacity: bindingValue(binding, "stadiumCapacity"),
      leagueQid,
      leagueName: bindingValue(binding, "leagueLabel"),
      organizerQid,
      organizerName: bindingValue(binding, "organizerLabel"),
      officialWebsite: bindingValue(binding, "officialWebsite"),
      competitionClassQid: qidFromUri(bindingValue(binding, "competitionClass")),
      leagueLevel: bindingValue(binding, "leagueLevel"),
      externalIds,
      sourceLicense: "CC0-1.0",
    },
    assetCandidates,
    facts,
    rawPayload: { qid, classQid, countryQid, foundedOn, birthDate, teamQid, externalIds },
  };
}

export function buildFootballEntitiesQuery({ countryQids = COUNTRY_QIDS, limit = 150 } = {}) {
  const countries = countryQids.map(qid => "wd:" + qid).join(" ");
  const classes = Object.entries(CLASS_TYPES)
    .filter(([, type]) => !["player", "coach"].includes(type))
    .map(([qid]) => "wd:" + qid).join(" ");
  const safeLimit = Math.max(1, Math.min(250, Number(limit) || 150));
  return [
    "SELECT DISTINCT ?item ?class ?itemLabel ?itemAltLabel ?nameAr ?nameEn",
    "       ?country ?countryLabel ?countryCode ?inception ?stadium ?stadiumLabel",
    "       ?image ?logo ?officialWebsite",
    "WHERE {",
    "  VALUES ?class { " + classes + " }",
    "  VALUES ?country { " + countries + " }",
    "  ?item wdt:P31 ?class; wdt:P17 ?country.",
    "  ?item rdfs:label ?itemLabel. FILTER(LANG(?itemLabel) = \"en\")",
    "  ?country rdfs:label ?countryLabel. FILTER(LANG(?countryLabel) = \"en\")",
    "  OPTIONAL { ?country wdt:P297 ?countryCode. }",
    "  OPTIONAL { ?item rdfs:label ?nameAr. FILTER(LANG(?nameAr) = \"ar\") }",
    "  OPTIONAL { ?item skos:altLabel ?itemAltLabel. FILTER(LANG(?itemAltLabel) = \"en\") }",
    "  OPTIONAL { ?item wdt:P571 ?inception. }",
    "  OPTIONAL { ?item wdt:P115 ?stadium. }",
    "  OPTIONAL { ?stadium rdfs:label ?stadiumLabel. FILTER(LANG(?stadiumLabel) = \"en\") }",
    "  OPTIONAL { ?item wdt:P18 ?image. }",
    "  OPTIONAL { ?item wdt:P154 ?logo. }",
    "  OPTIONAL { ?item wdt:P856 ?officialWebsite. }",
    "}",
    "LIMIT " + safeLimit
  ].join("\n");
}

export function buildFootballPeopleQuery({ countryQids = COUNTRY_QIDS, limit = 100 } = {}) {
  const countries = countryQids.map(qid => "wd:" + qid).join(" ");
  const safeLimit = Math.max(1, Math.min(200, Number(limit) || 100));
  return [
    "SELECT DISTINCT ?item ?class ?itemLabel ?itemAltLabel ?nameAr ?nameEn",
    "       ?country ?countryLabel ?countryCode ?birthDate ?image ?team ?teamLabel",
    "       ?position ?positionLabel",
    "WHERE {",
    "  VALUES ?class { wd:Q937857 wd:Q628099 }",
    "  VALUES ?country { " + countries + " }",
    "  ?item wdt:P106 ?class; wdt:P27 ?country.",
    "  ?item rdfs:label ?itemLabel. FILTER(LANG(?itemLabel) = \"en\")",
    "  ?country rdfs:label ?countryLabel. FILTER(LANG(?countryLabel) = \"en\")",
    "  OPTIONAL { ?country wdt:P297 ?countryCode. }",
    "  OPTIONAL { ?item rdfs:label ?nameAr. FILTER(LANG(?nameAr) = \"ar\") }",
    "  OPTIONAL { ?item skos:altLabel ?itemAltLabel. FILTER(LANG(?itemAltLabel) = \"en\") }",
    "  OPTIONAL { ?item wdt:P569 ?birthDate. }",
    "  OPTIONAL { ?item wdt:P18 ?image. }",
    "  OPTIONAL { ?item wdt:P54 ?team. }",
    "  OPTIONAL { ?team rdfs:label ?teamLabel. FILTER(LANG(?teamLabel) = \"en\") }",
    "  OPTIONAL { ?item wdt:P413 ?position. }",
    "  OPTIONAL { ?position rdfs:label ?positionLabel. FILTER(LANG(?positionLabel) = \"en\") }",
    "}",
    "LIMIT " + safeLimit
  ].join("\n");
}

export function createWikidataClient(options = {}) {
  const getJson = createJsonClient({
    source: "wikidata",
    minIntervalMs: 15_000,
    maxRequests: 10,
    timeoutMs: 55_000,
    maxRetries: 0,
    defaultHeaders: {
      accept: "application/sparql-results+json",
      "user-agent": "KORAX-World-Data-Engine/1.0 (https://github.com/shotalx2-lgtm/korax-)",
    },
    ...options,
  });

  return {
    async fetchFootballEntities(queryOptions = {}) {
      const query = buildFootballEntitiesQuery(queryOptions);
      const url = `${SPARQL_ENDPOINT}?query=${encodeURIComponent(query)}&format=json`;
      const payload = await getJson(url, { timeoutMs: 55_000 });
      const bindings = payload?.results?.bindings;
      return Array.isArray(bindings) ? bindings.map(normalizeWikidataBinding).filter(Boolean) : [];
    },
    async fetchFootballPeople(queryOptions = {}) {
      const query = buildFootballPeopleQuery(queryOptions);
      const url = `${SPARQL_ENDPOINT}?query=${encodeURIComponent(query)}&format=json`;
      const payload = await getJson(url, { timeoutMs: 55_000 });
      const bindings = payload?.results?.bindings;
      return Array.isArray(bindings) ? bindings.map(normalizeWikidataBinding).filter(Boolean) : [];
    },
    getStats: () => getJson.getStats(),
  };
}

export const wikidataConstants = { COUNTRY_QIDS, CLASS_TYPES };
