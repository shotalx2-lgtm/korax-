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
function normalizeSearchText(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\\u0300-\\u036f]/g, "")
    .toLocaleLowerCase()
    .replace(/[^\\p{L}\\p{N}]+/gu, " ")
    .trim();
}
export function normalizeCommonsSearchResult(page, entity) {
  const fileName = normalizeFileName(page?.title);
  const entityId = String(entity?.id || entity?.entityId || "");
  const entityType = String(entity?.entity_type || entity?.entityType || "");
  const name = String(entity?.name || entity?.canonical_name || entity?.name_en || "");
  if (!fileName || !entityId || !["club", "national", "player"].includes(entityType)) return null;
  if (!/\\.(?:svg|png|jpe?g|webp)$/i.test(fileName)) return null;
  const titleKey = normalizeSearchText(fileName.replace(/\\.[^.]+$/, ""));
  const nameKey = normalizeSearchText(name);
  const tokens = nameKey.split(/\\s+/).filter(token => token.length > 2 && !["football", "club", "team", "fc", "afc", "cf", "sc"].includes(token));
  if (!tokens.length || !tokens.every(token => titleKey.includes(token))) return null;
  const isPlayer = entityType === "player";
  const extension = fileName.split(".").pop().toLowerCase();
  if (isPlayer && !["jpg", "jpeg", "png", "webp"].includes(extension)) return null;
  if (!isPlayer && !["svg", "png"].includes(extension)) return null;
  return {
    entityId,
    fileName,
    assetType: isPlayer ? "portrait" : "logo",
    searchMatchedName: name,
  };
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
    async searchEntityFiles(entities, limit = 25) {
      const candidates = [];
      const selected = (Array.isArray(entities) ? entities : [])
        .filter(entity => ["club", "national", "player"].includes(String(entity?.entity_type || entity?.entityType || "")))
        .slice(0, Math.max(0, Math.min(25, Number(limit) || 25)));
      for (const entity of selected) {
        const name = String(entity?.name || entity?.canonical_name || entity?.name_en || "").trim();
        if (!name) continue;
        const params = new URLSearchParams({
          action: "query",
          format: "json",
          formatversion: "2",
          list: "search",
          srnamespace: "6",
          srsearch: '"' + name + '"',
          srlimit: "6",
        });
        const payload = await getJson(`${API}?${params.toString()}`);
        const pages = Array.isArray(payload?.query?.search) ? payload.query.search : [];
        const matches = pages.map(page => normalizeCommonsSearchResult(page, entity)).filter(Boolean);
        if (matches.length) candidates.push(matches[0]);
      }
      return candidates;
    },
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

export const commonsNormalizers = { normalizeCommonsPage, normalizeLicense, normalizeCommonsSearchResult };
