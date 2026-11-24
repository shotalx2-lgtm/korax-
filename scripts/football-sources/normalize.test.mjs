import test from "node:test";
import assert from "node:assert/strict";
import {
  normalizeName,
  normalizeCountryCode,
  createMatchFingerprint,
  scoreEntityMergeCandidate,
} from "./normalize.mjs";

test("normalizes Arabic variants, diacritics, tatweel and English accents", () => {
  assert.equal(normalizeName("مُحَمَّـد صَلاح"), "محمد صلاح");
  assert.equal(normalizeName("أَحْمَد"), "احمد");
  assert.equal(normalizeName("José Álvarez"), "jose alvarez");
});

test("normalizes ISO country codes and rejects free-form values", () => {
  assert.equal(normalizeCountryCode("eg"), "EG");
  assert.equal(normalizeCountryCode("EGY"), null);
  assert.equal(normalizeCountryCode(""), null);
});

test("match fingerprint stays stable after a reschedule when round is known", () => {
  const original = createMatchFingerprint({
    competitionName: "Premier League",
    season: "2026/27",
    round: "Matchday 12",
    matchDate: "2026-11-20",
    homeTeam: "Manchester United",
    awayTeam: "Chelsea",
  });
  const rescheduled = createMatchFingerprint({
    competitionName: "Premier League",
    season: "2026/27",
    round: "Matchday 12",
    matchDate: "2026-11-27",
    homeTeam: "Manchester United",
    awayTeam: "Chelsea",
  });
  assert.equal(original.fingerprint, rescheduled.fingerprint);
  assert.equal(original.stableAcrossReschedule, true);
});

test("uses date when no round exists so repeated fixtures do not collide", () => {
  const first = createMatchFingerprint({
    competition: "Cup",
    season: "2026",
    matchDate: "2026-11-20",
    home: "Club A",
    away: "Club B",
  });
  const second = createMatchFingerprint({
    competition: "Cup",
    season: "2026",
    matchDate: "2026-11-27",
    home: "Club A",
    away: "Club B",
  });
  assert.notEqual(first.fingerprint, second.fingerprint);
  assert.equal(first.stableAcrossReschedule, false);
});

test("similar names create review candidates but never authorize automatic merges", () => {
  const result = scoreEntityMergeCandidate(
    { entityType: "player", name: "Mohamed Salah", countryCode: "EG" },
    { entityType: "player", name: "Mohamed Salah", countryCode: "EG" },
  );
  assert.equal(result.score, 1);
  assert.equal(result.autoMergeAllowed, false);
  assert.equal(result.reason, "similar_name_and_same_country");
  assert.equal(scoreEntityMergeCandidate(
    { entityType: "player", name: "Mohamed Salah" },
    { entityType: "club", name: "Mohamed Salah" },
  ).score, 0);
});
