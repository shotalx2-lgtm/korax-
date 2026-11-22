import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

function installCache() {
  const original = globalThis.caches;
  globalThis.caches = { default: { match: async () => undefined, put: async () => undefined } };
  return () => {
    if (original === undefined) delete globalThis.caches;
    else globalThis.caches = original;
  };
}

test("health endpoint confirms both data sources need no API key", async () => {
  const response = await worker.fetch(new Request("https://korax.test/health"), {});
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.ok, true);
  assert.equal(body.provider, "SportScore");
  assert.equal(body.apiKeyRequired, false);
  assert.equal(body.newsProvider, "publisher-rss");
});

test("SportScore fixtures normalize into KORAX match contract without an API key", async () => {
  const originalFetch = globalThis.fetch;
  const restoreCache = installCache();
  let requestedUrl = "";
  globalThis.fetch = async (input, init) => {
    requestedUrl = String(input);
    assert.equal(new Headers(init.headers).get("x-api-key"), null);
    assert.equal(new Headers(init.headers).get("authorization"), null);
    return new Response(JSON.stringify({
      fixtures: [{
        id: 987, slug: "al-ahly-vs-zamalek",
        home: "Al Ahly", away: "Zamalek",
        home_logo: "https://images.example/home.png",
        away_logo: "https://images.example/away.png",
        home_score: 2, away_score: 1,
        status: "live", status_text: "مباشر",
        time: "2026-11-22T18:00:00Z",
        competition: "Egyptian Premier League",
      }],
    }), { status: 200, headers: { "content-type": "application/json" } });
  };
  try {
    const response = await worker.fetch(
      new Request("https://korax.test/api/matches?live=1", { headers: { Origin: "https://app.example" } }),
      { ALLOWED_ORIGINS: "*" }
    );
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("access-control-allow-origin"), "*");
    assert.match(requestedUrl, /sportscore\.com\/api\/v1\/fixtures\//);
    assert.match(requestedUrl, /status=live/);
    const body = await response.json();
    assert.equal(body.source, "sportscore");
    assert.equal(body.data[0].home, "Al Ahly");
    assert.equal(body.data[0].away, "Zamalek");
    assert.equal(body.data[0].homeScore, 2);
    assert.equal(body.data[0].awayScore, 1);
    assert.equal(body.data[0].homeTeam.crest, "https://images.example/home.png");
    assert.equal(body.data[0].id, "al-ahly-vs-zamalek");
  } finally {
    globalThis.fetch = originalFetch;
    restoreCache();
  }
});

test("RSS news aggregates Arabic and international feeds with publisher images", async () => {
  const originalFetch = globalThis.fetch;
  const restoreCache = installCache();
  const calls = [];
  globalThis.fetch = async (input) => {
    const url = String(input);
    calls.push(url);
    const arabic = /btolat|youm7|hesport|aljazeera|skynewsarabia/.test(url);
    const sourceTitle = arabic ? "خبر جديد عن كرة القدم والدوري" : "Latest football transfer and league news";
    const xml = "<?xml version=\"1.0\"?><rss><channel><item>" +
      "<title>" + sourceTitle + "</title>" +
      "<link>https://publisher.example/" + calls.length + "</link>" +
      "<description>Short football news summary for KORAX testing.</description>" +
      "<pubDate>Sun, 22 Nov 2026 17:00:00 GMT</pubDate>" +
      "<enclosure url=\"https://images.example/" + calls.length + ".jpg\" type=\"image/jpeg\"/>" +
      "</item></channel></rss>";
    return new Response(xml, { status: 200, headers: { "content-type": "application/rss+xml" } });
  };
  try {
    const response = await worker.fetch(new Request("https://korax.test/api/news?per_page=100"), {});
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.source, "publisher-rss");
    assert.equal(body.language, "ar+en");
    assert.equal(body.feedsTotal, 26);
    assert.equal(body.feedsAvailable, 26, JSON.stringify(body.feedErrors));
    assert.equal(body.data.length, 26);
    assert.ok(body.data.some(item => item.language === "ar"));
    assert.ok(body.data.some(item => item.language === "en"));
    assert.ok(body.data.every(item => item.imageUrl?.startsWith("https://images.example/")));
    assert.equal(calls.length, 26);
    assert.ok(calls.every(url => !url.includes("news.google.com")));
  } finally {
    globalThis.fetch = originalFetch;
    restoreCache();
  }
});

test("fantasy uses documented SportScore league leaderboards and exposes their limited scope", async () => {
  const originalFetch = globalThis.fetch;
  const restoreCache = installCache();
  const requested = [];
  globalThis.fetch = async (input, init) => {
    const url = new URL(String(input));
    requested.push(url);
    assert.equal(new Headers(init.headers).get("x-api-key"), null);
    assert.equal(new Headers(init.headers).get("authorization"), null);
    if (url.pathname === "/api/v1/search/") {
      return new Response(JSON.stringify({
        competitions: [{ name: "Saudi Pro League", slug: "saudi-pro-2026" }],
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
    const slug = url.searchParams.get("slug") || "unknown";
    return new Response(JSON.stringify({
      players: [{
        player: {
          id: "player-" + slug, slug: "player-" + slug,
          name: "Test Striker", position: "Forward",
          image: "https://images.example/player.png",
        },
        team: { name: "Test Club", slug: "test-club" },
        goals: 10, assists: 3, appearances: 12,
      }],
    }), { status: 200, headers: { "content-type": "application/json" } });
  };
  try {
    const response = await worker.fetch(new Request("https://korax.test/api/fantasy/players"), {});
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.source, "sportscore");
    assert.equal(body.leagues.length, 6);
    assert.equal(body.data.length, 6);
    assert.equal(body.meta.catalogueScope, "top_scorers_and_assists_only");
    assert.equal(body.meta.roundScoringAvailable, false);
    assert.ok(body.data.every(player => player.position === "FWD"));
    assert.ok(requested.some(url => url.pathname === "/api/v1/search/"));
    assert.ok(requested.filter(url => url.pathname === "/api/v1/topscorers/").length === 12);
    assert.ok(requested.some(url => url.searchParams.get("slug") === "jednm9whz0ryox8"));
    assert.ok(requested.some(url => url.searchParams.get("slug") === "saudi-pro-2026"));
  } finally {
    globalThis.fetch = originalFetch;
    restoreCache();
  }
});

test("SportScore has no confirmed transfer endpoint, so the API does not invent transfer records", async () => {
  const response = await worker.fetch(new Request("https://korax.test/api/transfers?per_page=50"), {});
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.deepEqual(body.data, []);
  assert.equal(body.meta.available, false);
});
