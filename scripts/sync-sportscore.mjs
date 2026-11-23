const SPORTScore_BASE = "https://sportscore.com";
const SPORTScore_UA = "sportscore-mcp/0.3 (+https://sportscore.com/developers/)";
const SUPABASE_URL = process.env.SUPABASE_URL;
const PUBLISHABLE_KEY = process.env.SUPABASE_PUBLISHABLE_KEY;
import { selectDetailBatch } from "./sync-sportscore-batching.mjs";

function assert(value, message) { if (!value) throw new Error(message); }

async function sportJson(url) {
  const response = await fetch(url, {
    headers: { "user-agent": SPORTScore_UA, accept: "application/json" },
    signal: AbortSignal.timeout(20_000),
  });
  const body = await response.text();
  if (!response.ok) {
    const error = new Error("SportScore HTTP " + response.status + " for " + url + ": " + body.slice(0, 200));
    error.status = response.status;
    throw error;
  }
  try { return JSON.parse(body); }
  catch { throw new Error("SportScore returned invalid JSON for " + url); }
}

function listOf(value) {
  const root = value?.data ?? value;
  return Array.isArray(root) ? root : (root?.matches || root?.fixtures || root?.results || []);
}

function slugOf(match) {
  const url = String(match?.url || match?.match_url || match?.href || "");
  return String(match?.match_slug || match?.fixture_slug ||
    url.match(/\/match\/[^/]+\/([A-Za-z0-9_-]+)\/?(?:[?#].*)?$/i)?.[1] || match?.slug || "");
}

function detailSlugOf(match) {
  const url = String(match?.url || match?.match_url || match?.href || "");
  return String(match?.detail_slug ||
    url.match(/\/match\/([^/]+)\/[A-Za-z0-9_-]+\/?(?:[?#].*)?$/i)?.[1] ||
    match?.slug || slugOf(match));
}

async function mapLimit(items, concurrency, callback) {
  const result = new Array(items.length);
  let next = 0;
  async function run() {
    for (;;) {
      const index = next++;
      if (index >= items.length) return;
      try { result[index] = await callback(items[index], index); }
      catch (error) { result[index] = { error: String(error) }; }
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, run));
  return result;
}

async function getGitHubOidc() {
  const url = new URL(process.env.ACTIONS_ID_TOKEN_REQUEST_URL);
  url.searchParams.set("audience", "korax-sportscore-sync");
  const response = await fetch(url, {
    headers: { authorization: "Bearer " + process.env.ACTIONS_ID_TOKEN_REQUEST_TOKEN },
    signal: AbortSignal.timeout(20_000),
  });
  if (!response.ok) throw new Error("GitHub OIDC request failed: HTTP " + response.status);
  const value = await response.json();
  assert(value.value, "GitHub OIDC token missing");
  return value.value;
}

async function main() {
  assert(SUPABASE_URL && PUBLISHABLE_KEY, "Supabase sync configuration missing");
  assert(process.env.ACTIONS_ID_TOKEN_REQUEST_URL, "GitHub Actions OIDC is not enabled for this workflow");

  const now = new Date();
  const today = now.toISOString().slice(0, 10);
  const minute = now.getUTCMinutes();
  const fullSync = process.env.KORAX_FORCE_FULL_SYNC === "true" ||
    process.env.GITHUB_EVENT_NAME === "workflow_dispatch" || minute % 15 === 0;
  const standingsSync = process.env.KORAX_FORCE_FULL_SYNC === "true" ||
    process.env.GITHUB_EVENT_NAME === "workflow_dispatch" || minute === 0;

  const matchMap = new Map();
  const livePayload = await sportJson(SPORTScore_BASE + "/api/widget/matches/?sport=football&limit=50");
  for (const match of listOf(livePayload)) {
    const slug = slugOf(match);
    if (slug) matchMap.set(slug, match);
  }

  const competitions = [];
  if (fullSync) {
    const dates = [];
    for (let offset = -1; offset <= 7; offset++) {
      const date = new Date(now);
      date.setUTCDate(date.getUTCDate() + offset);
      dates.push(date.toISOString().slice(0, 10));
    }
    const schedules = await mapLimit(dates, 4, async date => ({
      date,
      payload: await sportJson(SPORTScore_BASE + "/api/v1/fixtures/?sport=football&date=" + date + "&limit=200"),
    }));
    for (let i = 0; i < schedules.length; i++) {
      const result = schedules[i];
      if (!result || result.error) {
        throw new Error("Fixture schedule failed for " + dates[i] + ": " + (result?.error || "unknown"));
      }
      for (const match of listOf(result.payload)) {
        const slug = slugOf(match);
        if (slug) matchMap.set(slug, match);
      }
    }
  }

  const matches = [...matchMap.values()];
  let details = [];
  if (fullSync) {
    const candidates = matches
      .filter(match => {
        const time = Date.parse(match.time || match.start_time || "");
        if (!Number.isFinite(time)) return false;
        return new Date(time).toISOString().slice(0, 10) === today &&
          !/cancel|postpon|abandon/i.test(String(match.status || ""));
      })
      .sort((a, b) => Date.parse(a.time || a.start_time) - Date.parse(b.time || b.start_time))
      .slice(0, 80);
    const batch = selectDetailBatch(candidates, minute, 20);
    console.log(
      "SportScore detail batch:",
      batch.batchIndex + 1,
      "of",
      batch.batchCount,
      "; candidates:",
      candidates.length,
      "; selected:",
      batch.items.length,
    );

    const detailResults = [];
    for (const match of batch.items) {
      const slug = slugOf(match);
      try {
        const detail = await sportJson(
          SPORTScore_BASE + "/api/widget/match/?sport=football&slug=" + encodeURIComponent(detailSlugOf(match)),
        );
        detailResults.push({ slug, detail });
      } catch (error) {
        console.error("Detail fetch warning:", String(error));
        if (error?.status === 429) {
          console.error("SportScore rate limit reached; stopping this detail batch early to avoid further requests.");
          break;
        }
      }
      await new Promise(resolve => setTimeout(resolve, 750));
    }

    details = detailResults.filter(result => result && result.slug && result.detail);
    console.log("SportScore detail fetch results:", details.length, "of", batch.items.length, "selected from", candidates.length);
  }

  if (standingsSync) {
    const targets = [
      { name: "الدوري الإنجليزي", country: "إنجلترا", queries: ["English Premier League", "Premier League"] },
      { name: "الدوري الإسباني", country: "إسبانيا", queries: ["Spanish La Liga", "La Liga"] },
      { name: "الدوري الفرنسي", country: "فرنسا", queries: ["French Ligue 1", "Ligue 1"] },
      { name: "الدوري الإيطالي", country: "إيطاليا", queries: ["Italian Serie A", "Serie A"] },
      { name: "الدوري الألماني", country: "ألمانيا", queries: ["Bundesliga"] },
      { name: "الدوري السعودي", country: "السعودية", queries: ["Saudi Pro League", "Saudi Professional League", "Saudi Roshn League", "Roshn Saudi League"] },
      { name: "دوري أبطال أوروبا", country: "أوروبا", queries: ["UEFA Champions League"] },
    ];

    const discovered = await mapLimit(targets, 3, async target => {
      for (const query of target.queries) {
        const payload = await sportJson(SPORTScore_BASE + "/api/v1/search/?sport=football&limit=30&q=" + encodeURIComponent(query));
        const root = payload?.data ?? payload;
        const items = [];
        for (const key of ["competitions", "leagues", "tournaments", "cups"]) {
          if (Array.isArray(root?.[key])) items.push(...root[key]);
        }
        if (Array.isArray(root?.results)) items.push(...root.results);
        const wanted = target.name.includes("الإنجليزي") ? ["english premier league", "premier league"]
          : target.name.includes("الإسباني") ? ["spanish la liga", "la liga"]
          : target.name.includes("الفرنسي") ? ["french ligue 1", "ligue 1"]
          : target.name.includes("الإيطالي") ? ["italian serie a", "serie a"]
          : target.name.includes("الألماني") ? ["bundesliga"]
          : target.name.includes("السعودي") ? ["saudi pro league", "saudi professional league", "saudi roshn league", "roshn saudi league"]
          : ["uefa champions league"];
        const normalName = item => String(item?.name || item?.title || item?.common_name || "").trim().toLowerCase();
        const selected = items.find(item => wanted.includes(normalName(item))) ||
          items.find(item => wanted.some(name => normalName(item).includes(name)));
        if (!selected) continue;
        const url = String(selected.url || selected.href || "");
        const pathSlug = url.match(/\/competition\/[^/]+\/([^/]+)\/[A-Za-z0-9_-]{8,}/i)?.[1] || "";
        const slug = String(selected.slug || selected.competition_slug || selected.league_slug || selected.url_slug || pathSlug || "");
        if (slug) return { slug, name: target.name, country: target.country, matchedName: normalName(selected) };
      }
      return null;
    });

    const validTargets = discovered.filter(Boolean);
    console.log("SportScore resolved standings slugs:", JSON.stringify(validTargets));
    const standingsResults = await mapLimit(validTargets, 3, async target => ({
      ...target,
      standings: await sportJson(SPORTScore_BASE + "/api/widget/standings/?sport=football&slug=" + encodeURIComponent(target.slug)),
    }));
    for (const result of standingsResults) {
      if (!result || result.error) {
        console.error("Standings fetch warning:", result?.error || "unknown");
        continue;
      }
      competitions.push(result);
    }
  }

  const token = await getGitHubOidc();
  const payload = { matches, details, competitions, syncedAt: new Date().toISOString() };
  const response = await fetch(SUPABASE_URL + "/functions/v1/korax-sportscore-sync", {
    method: "POST",
    headers: {
      authorization: "Bearer " + token,
      apikey: PUBLISHABLE_KEY,
      "content-type": "application/json",
    },
    body: JSON.stringify(payload),
    signal: AbortSignal.timeout(60_000),
  });
  const body = await response.text();
  if (!response.ok) throw new Error("Supabase sync failed: HTTP " + response.status + " " + body.slice(0, 600));
  console.log("SportScore -> Supabase sync:", body);
  console.log("Collected " + matches.length + " matches, " + details.length + " details, " + competitions.length + " standings tables.");
}

main().catch(error => {
  console.error("KORAX SportScore sync failed:", error);
  process.exitCode = 1;
});
