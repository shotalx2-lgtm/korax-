import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

test("health endpoint does not require provider credentials", async () => {
  const response = await worker.fetch(new Request("https://koora.test/health"), {});
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.ok, true);
  assert.equal(body.providerConfigured, false);
});

test("live fixtures are normalized and returned with CORS", async () => {
  const originalFetch = globalThis.fetch;
  const originalCaches = globalThis.caches;
  globalThis.caches = {
    default: {
      match: async () => undefined,
      put: async () => undefined,
    },
  };
  globalThis.fetch = async (input, init) => {
    assert.match(String(input), /\/livescores\/inplay/);
    assert.equal(new Headers(init.headers).get("authorization"), "unit-test-token");
    return new Response(JSON.stringify({
      data: [{
        id: 987,
        starting_at: "2026-11-19 18:00:00",
        state: { name: "Inplay" },
        league: { name: "Example League" },
        participants: [
          { id: 11, name: "Home Club", image_path: "https://images.example/home.png", meta: { location: "home" } },
          { id: 22, name: "Away Club", image_path: "https://images.example/away.png", meta: { location: "away" } }
        ],
        scores: [
          { participant_id: 11, description: "CURRENT", score: { goals: 2 } },
          { participant_id: 22, description: "CURRENT", score: { goals: 1 } }
        ],
        events: [{ minute: 32, type: { name: "Goal" }, player: { name: "Player A" }, participant: { name: "Home Club" } }],
        lineups: [{ player: { name: "Player A" }, position: { name: "Forward" }, team: { name: "Home Club" } }],
        statistics: [{ type: { name: "Ball Possession" }, data: { value: "58%" }, participant_id: 11 }]
      }]
    }), { status: 200, headers: { "content-type": "application/json" } });
  };
  try {
    const response = await worker.fetch(
      new Request("https://koora.test/api/matches?live=1", { headers: { Origin: "https://app.example" } }),
      { SPORTMONKS_API_TOKEN: "unit-test-token", ALLOWED_ORIGINS: "*" }
    );
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("access-control-allow-origin"), "*");
    const body = await response.json();
    assert.equal(body.data[0].home, "Home Club");
    assert.equal(body.data[0].away, "Away Club");
    assert.equal(body.data[0].homeScore, 2);
    assert.equal(body.data[0].awayScore, 1);
    assert.equal(body.data[0].events[0].player_name, "Player A");
    assert.equal(body.data[0].lineups[0].player_name, "Player A");
    assert.equal(body.data[0].statistics[0].name, "Ball Possession");
  } finally {
    globalThis.fetch = originalFetch;
    if (originalCaches === undefined) delete globalThis.caches;
    else globalThis.caches = originalCaches;
  }
});

test("provider routes fail closed when the server secret is missing", async () => {
  const originalCaches = globalThis.caches;
  globalThis.caches = { default: { match: async () => undefined, put: async () => undefined } };
  try {
    const response = await worker.fetch(new Request("https://koora.test/api/matches"), {});
    assert.equal(response.status, 503);
    const body = await response.json();
    assert.match(body.error, /not configured/);
  } finally {
    if (originalCaches === undefined) delete globalThis.caches;
    else globalThis.caches = originalCaches;
  }
});


test("Arabic news endpoint aggregates Arabic feed items with their image", async () => {
  const originalFetch = globalThis.fetch;
  const originalCaches = globalThis.caches;
  globalThis.caches = { default: { match: async () => undefined, put: async () => undefined } };
  globalThis.fetch = async (input) => {
    const url = String(input);
    if (url.includes("youm7.com")) {
      return new Response(`<?xml version="1.0"?>
        <rss><channel><item>
          <title>خبر كروي عربي للاختبار</title>
          <link>https://sports.example/article-1</link>
          <description><![CDATA[ملخص الخبر الرياضي]]></description>
          <media:content url="https://images.example/article-1.jpg" />
          <pubDate>Sat, 21 Nov 2026 12:00:00 GMT</pubDate>
        </item></channel></rss>`, { status: 200 });
    }
    return new Response("<rss><channel></channel></rss>", { status: 200 });
  };
  try {
    const response = await worker.fetch(new Request("https://koora.test/api/news?per_page=10"), {});
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.language, "ar");
    assert.equal(body.data.length, 1);
    assert.equal(body.data[0].title, "خبر كروي عربي للاختبار");
    assert.equal(body.data[0].imageUrl, "https://images.example/article-1.jpg");
    assert.equal(body.data[0].source, "اليوم السابع");
  } finally {
    globalThis.fetch = originalFetch;
    if (originalCaches === undefined) delete globalThis.caches;
    else globalThis.caches = originalCaches;
  }
});
