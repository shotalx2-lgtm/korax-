import { createHash } from "node:crypto";

export function normalizeName(value) {
  return String(value ?? "")
    .normalize("NFKD")
    .replace(/\p{M}/gu, "")
    .toLowerCase()
    .replace(/[أإآٱ]/g, "ا")
    .replace(/ى/g, "ي")
    .replace(/ؤ/g, "و")
    .replace(/ئ/g, "ي")
    .replace(/ـ/g, "")
    .replace(/[’'".,()\[\]{}:;!?/\\_-]+/g, " ")
    .replace(/[^\p{L}\p{N}\s]/gu, " ")
    .replace(/\s+/g, " ")
    .trim();
}

export function normalizeCountryCode(value) {
  const code = String(value ?? "").trim().toUpperCase();
  return /^[A-Z]{2}$/.test(code) ? code : null;
}

export function createMatchFingerprint(match) {
  const competition = normalizeName(match?.competitionName || match?.competition || "");
  const season = normalizeName(match?.season || "");
  const home = normalizeName(match?.homeTeam || match?.home || "");
  const away = normalizeName(match?.awayTeam || match?.away || "");
  const round = normalizeName(match?.round || match?.matchday || match?.stage || "");
  const date = String(match?.matchDate || match?.date || "").slice(0, 10);

  if (!competition || !season || !home || !away) {
    throw new TypeError("competition, season, home team and away team are required for a match fingerprint");
  }

  // A known round/leg keeps the identity stable when kickoff is rescheduled.
  // Without a round, include the date to avoid merging repeat fixtures blindly.
  const parts = round
    ? [competition, season, round, home, away]
    : [competition, season, date, home, away];
  const identity = parts.join("|");
  return {
    fingerprint: createHash("sha256").update(identity).digest("hex"),
    stableAcrossReschedule: Boolean(round),
    identity,
  };
}

function trigrams(value) {
  const padded = `  ${normalizeName(value)}  `;
  const set = new Set();
  for (let index = 0; index <= padded.length - 3; index += 1) {
    set.add(padded.slice(index, index + 3));
  }
  return set;
}

export function scoreEntityMergeCandidate(left, right) {
  if (!left || !right || left.entityType !== right.entityType) {
    return { score: 0, reason: "entity_type_mismatch", autoMergeAllowed: false };
  }
  const a = trigrams(left.name || left.nameAr || left.nameEn);
  const b = trigrams(right.name || right.nameAr || right.nameEn);
  const intersection = [...a].filter(item => b.has(item)).length;
  const nameScore = a.size + b.size === 0 ? 0 : (2 * intersection) / (a.size + b.size);
  const sameCountry = Boolean(left.countryCode && right.countryCode &&
    String(left.countryCode).toUpperCase() === String(right.countryCode).toUpperCase());
  const score = Math.min(1, nameScore * 0.8 + (sameCountry ? 0.2 : 0));
  const reason = sameCountry ? "similar_name_and_same_country" : "similar_name_only";
  // Similarity is a review signal only. Provider IDs or an explicit reviewed link are required to merge.
  return { score: Number(score.toFixed(3)), reason, autoMergeAllowed: false };
}
