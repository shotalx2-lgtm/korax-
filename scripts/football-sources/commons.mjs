import { createJsonClient } from "./http.mjs";

const API = "https://commons.wikimedia.org/w/api.php";

function plainText(value) {
  return String(value || "")
    .replace(/<[^>]*>/g, " ")
    .replace(/&nbsp;/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/\s+/g, " ")
    .trim() || null;
}
function metadataValue(metadata, key) {
  return plainText(metadata?.[key]?.value);
}

function normalizeFileName(value) {
  return String(value || "").replace(/^File:/i, "").replace(/_/g, " ").normalize("NFC").trim();
}
function normalizeLicense(shortName, usageTerms) {
  const value = `${shortName || ""} ${usageTerms || ""}`.trim();
  if (/non.?commercial|\bNC\b|no commercial/i.test(value)) return { allowed: false, code: shortName || "non-commercial" };
  if (/no.?derivatives|\bND\b|fair use|non.?free|all rights reserved/i.test(value)) return { allowed: false, code: shortName || "restricted" };
  if (/CC0|CC BY|CC-BY|Creative Commons Attribution|Public domain/i.test(value)) {
    return { allowed: true, code: shortName || "Creative Commons" };
  }
  return { allowed: false, code: shortName || "unknown" };
}

export function normalizeCommonsPage(page, candidates = []) {
  const info = Array.isArray(page?.imageinfo) ? page.imageinfo[0] : null;
  const title = String(page?.title || "");
  const fileName = normalizeFileName(title);
  const candidate = candidates.find(item => normalizeFileName(item.fileName) === fileName);
  if (!info || !candidate || page?.missing) return null;

  const ext = info.extmetadata || {};
  const licenseShortName = metadataValue(ext, "LicenseShortName");
  const licenseUrl = metadataValue(ext, "LicenseUrl");
  const usageTerms = metadataValue(ext, "UsageTerms");
  const license = normalizeLicense(licenseShortName, usageTerms);
  const creator = metadataValue(ext, "Artist");
  const credit = metadataValue(ext, "Credit");
  const attributionRequired = String(metadataValue(ext, "AttributionRequired") || "").toLowerCase() === "true";
  const attributionText = [creator, credit, licenseShortName, info.descriptionurl].filter(Boolean).join(" — ");
  const isLogo = candidate.assetType === "logo";

  return {
    entityId: candidate.entityId || null,
    fileName,
    assetType: candidate.assetType,
    assetUrl: info.url || candidate.specialFileUrl,
    sourceKey: "wikimedia_commons",
    sourcePageUrl: info.descriptionurl || `https://commons.wikimedia.org/wiki/${encodeURIComponent(title.replace(/ /g, "_"))}`,
    licenseCode: license.code,
    licenseUrl,
    creator,
    attributionText: attributionRequired || creator || credit ? attributionText : null,
    licenseStatus: !license.allowed ? "blocked" : isLogo ? "unverified" : "verified",
    isPrimary: false,
    width: Number(info.width) || null,
    height: Number(info.height) || null,
    metadata: {
      usageTerms,
      attributionRequired,
      credit,
      trademarkReviewRequired: isLogo,
      reviewNote: isLogo ? "Copyright license checked; logo/trademark use still requires review." : null,
    },
  };
}

export function createCommonsClient(options = {}) {
  const getJson = createJsonClient({
    source: "wikimedia_commons",
    minIntervalMs: 1_000,
    maxRequests: 60,
    timeoutMs: 20_000,
    maxRetries: 1,
    defaultHeaders: { "user-agent": "KORAX-World-Data-Engine/1.0 (https://github.com/shotalx2-lgtm/korax-)" },
    ...options,
  });

  return {
    async verifyAssets(candidates) {
      const allCandidates = (Array.isArray(candidates) ? candidates : []).filter(item => item?.fileName);
      const uniqueFiles = [...new Set(allCandidates.map(item => normalizeFileName(item.fileName)))];
      const verified = [];
      for (let index = 0; index < uniqueFiles.length; index += 20) {
        const fileChunk = uniqueFiles.slice(index, index + 20);
        const params = new URLSearchParams({
          action: "query",
          format: "json",
          formatversion: "2",
          prop: "imageinfo",
          iiprop: "url|extmetadata",
          iiextmetadatalanguage: "en",
          titles: fileChunk.map(fileName => `File:${fileName}`).join("|"),
        });
        const payload = await getJson(`${API}?${params.toString()}`);
        const pages = Array.isArray(payload?.query?.pages) ? payload.query.pages : [];
        for (const page of pages) {
          const fileName = normalizeFileName(page?.title);
          for (const candidate of allCandidates.filter(item => normalizeFileName(item.fileName) === fileName)) {
            const normalized = normalizeCommonsPage(page, [candidate]);
            if (normalized) verified.push(normalized);
          }
        }
      }
      return verified;
    },
    getStats: () => getJson.getStats(),
  };
}

export const commonsNormalizers = { normalizeCommonsPage, normalizeLicense };
