import { createJsonClient } from "./http.mjs";
import { normalizeName } from "./normalize.mjs";

const LANGUAGES = new Set(["en", "ar", "fr", "es"]);

export function normalizeWikipediaPage(payload, expectedTitle, language = "en") {
  const page = Object.values(payload?.query?.pages || {})[0];
  if (!page || page.missing !== undefined || !page.pageid || !page.title) return null;
  if (!LANGUAGES.has(language)) return null;
  if (normalizeName(page.title) !== normalizeName(expectedTitle)) return null;

  const extract = String(page.extract || "").replace(/\s+/g, " ").trim();
  if (!extract) return null;
  return {
    pageId: String(page.pageid),
    title: String(page.title),
    summary: extract.slice(0, 1_200),
    url: String(page.fullurl || ("https://" + language + ".wikipedia.org/wiki/" + encodeURIComponent(String(page.title).replace(/ /g, "_")))),
    language,
    license: "CC-BY-SA-4.0/GFDL",
  };
}

export function createWikipediaClient(options = {}) {
  const getJson = createJsonClient({
    source: "wikipedia",
    minIntervalMs: 1_500,
    maxRequests: 40,
    timeoutMs: 15_000,
    maxRetries: 1,
    defaultHeaders: {
      "user-agent": "KORAX-World-Data-Sync/1.0 (https://github.com/shotalx2-lgtm/korax-; contact: support@korax.app)",
      accept: "application/json",
    },
    ...options,
  });

  return {
    async pageSummary(title, language = "en") {
      if (!LANGUAGES.has(language)) throw new Error("Unsupported Wikipedia language");
      const params = new URLSearchParams({
        action: "query",
        format: "json",
        prop: "extracts|info",
        exintro: "1",
        explaintext: "1",
        inprop: "url",
        redirects: "1",
        titles: String(title),
      });
      const payload = await getJson("https://" + language + ".wikipedia.org/w/api.php?" + params.toString());
      return normalizeWikipediaPage(payload, title, language);
    },
    getStats: () => getJson.getStats(),
  };
}
