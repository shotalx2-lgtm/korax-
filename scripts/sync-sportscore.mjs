const SPORTScore_BASE = "https://sportscore.com";
const SPORTScore_UA = "sportscore-mcp/0.3 (+https://sportscore.com/developers/)";
const SUPABASE_URL = process.env.SUPABASE_URL;
const PUBLISHABLE_KEY = process.env.SUPABASE_PUBLISHABLE_KEY;

function assert(value, message) { if (!value) throw new Error(message); }

async function sportJson(url) {
  const response = await fetch(url, {
    headers: { "user-agent": SPORTScore_UA, accept: "application/json" },
    signal: AbortSignal.timeout(20_000),
  });
  const body = await response.text();
  if (!response.ok) throw new Error("SportScore HTTP " + response.status + " for " + url + ": " + body.slice(0, 200));
  try { return JSON.parse(body); }
  catch { throw new Error("SportScore returned invalid JSON for " + url); }
}

function listOf(value) {
  const root = value?.data ?? value;
  return Array.isArray(root) ? root : (root?.matches || root?.fixtures || root?.results || []);
}

function slugOf(match) {
  const url = String(match?.url || match?.match_url || match?.href || "");
  return String(match?.slug || match?.match_slug || match?.fixture_slug ||
    url.match(/\/match\/[^/]+\/([A-Za-z0-9_-]+)\/?(?:[?#].*)?$/i)?.[1] || "");
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
      .slice(0, 20);
    const detailResults = await mapLimit(candidates, 4, async match => {
      const slug = slugOf(match);
      return {
        slug,
        detail: await sportJson(SPORTScore_BASE + "/api/widget/match/?sport=football&slug=" + encodeURIComponent(slug)),
      };
    });
    details = detailResults.filter(result => result && !result.error && result.slug && result.detail);
    for (const result of detailResults) if (result?.error) console.error("Detail fetch warning:", result.error);
  }

  if (standingsSync) {
    const targets = [
      { slug: "jednm9whz0ryox8", name: "الدوري الإنجليزي", country: "إنجلترا" },
      { slug: "vl7oqdehlyr510j", name: "الدوري الإسباني", country: "إسبانيا" },
      { slug: "yl5ergphnzr8k0o", name: "الدوري الفرنسي", country: "فرنسا" },
      { slug: "4zp5rzghp5q82w1", name: "الدوري الإيطالي", country: "إيطاليا" },
      { slug: "gy0or5jhg6qwzv3", name: "الدوري الألماني", country: "ألمانيا" },
      { slug: "saudi-pro-league", name: "الدوري السعودي", country: "السعودية" },
      { slug: "z8yomo4h7wq0j6l", name: "دوري أبطال أوروبا", country: "أوروبا" },
    ];
    const results = await mapLimit(targets, 3, async target => ({
      ...target,
      standings: await sportJson(SPORTScore_BASE + "/api/widget/standings/?sport=football&slug=" + encodeURIComponent(target.slug)),
    }));
    for (const result of results) {
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
