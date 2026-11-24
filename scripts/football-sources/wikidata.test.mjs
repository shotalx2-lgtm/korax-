import test from "node:test";
import assert from "node:assert/strict";
import { buildFootballEntitiesQuery, normalizeWikidataBinding, wikidataConstants } from "./wikidata.mjs";

const value = value => ({ value });

test("builds a bounded batched SPARQL query for football entities", () => {
  const query = buildFootballEntitiesQuery({ countryQids: ["Q79", "Q851"], limit: 2000 });
  assert.match(query, /wd:Q79 wd:Q851/);
  assert.match(query, /LIMIT 1000/);
  assert.match(query, /wd:Q476028/);
  assert.match(query, /wd:Q6979593/);
});

test("normalizes Wikidata QIDs, Arabic/English names and structured facts", () => {
  const record = normalizeWikidataBinding({
    item: value("http://www.wikidata.org/entity/Q7156"),
    class: value("http://www.wikidata.org/entity/Q476028"),
    nameEn: value("FC Barcelona"),
    nameAr: value("نادي برشلونة"),
    country: value("http://www.wikidata.org/entity/Q29"),
    countryCode: value("ES"),
    countryLabel: value("Spain"),
    inception: value("1899-11-29T00:00:00Z"),
    stadium: value("http://www.wikidata.org/entity/Q18656"),
    stadiumLabel: value("Spotify Camp Nou"),
    image: value("http://commons.wikimedia.org/wiki/Special:FilePath/Barcelona.jpg"),
    logo: value("http://commons.wikimedia.org/wiki/Special:FilePath/Barcelona%20crest.svg"),
    uefaTeamId: value("50080"),
    transfermarktTeamId: value("131"),
  });
  assert.equal(record.entityType, "club");
  assert.equal(record.externalId, "Q7156");
  assert.equal(record.nameAr, "نادي برشلونة");
  assert.equal(record.countryCode, "ES");
  assert.equal(record.foundedOn, "1899-11-29");
  assert.equal(record.metadata.stadiumQid, "Q18656");
  assert.equal(record.metadata.externalIds.uefaTeamId, "50080");
  assert.equal(record.assetCandidates.length, 2);
  assert.equal(record.assetCandidates[0].fileName, "Barcelona.jpg");
});

test("does not treat a QID as a club when the class is unknown", () => {
  const record = normalizeWikidataBinding({
    item: value("http://www.wikidata.org/entity/Q123"),
    class: value("http://www.wikidata.org/entity/Q999"),
    nameEn: value("Unknown"),
  });
  assert.equal(record, null);
});

test("exposes the curated country and entity class lists for controlled coverage", () => {
  assert.ok(wikidataConstants.COUNTRY_QIDS.includes("Q79"));
  assert.equal(wikidataConstants.CLASS_TYPES.Q6979593, "national");
});
