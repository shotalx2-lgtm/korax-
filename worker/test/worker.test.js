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


test("Arabic news endpoint fetches the named publishers without a news API key", async () => {
  const originalFetch = globalThis.fetch;
  const originalCaches = globalThis.caches;
  globalThis.caches = { default: { match: async () => undefined, put: async () => undefined } };
  const calls = [];
  globalThis.fetch = async (input) => {
    const url = String(input);
    calls.push(url);
    const article = (href, title, image, summary) =>
      '<a href="' + href + '"><article><h2>' + title + '</h2><img src="' + image + '" alt="' + title + '"><p>' + summary + '</p></article></a>';
    if (url.includes("365scores.com")) return new Response(
      article("https://www.365scores.com/ar/news/magazine/test-article/", "خبر من 365Scores عن كرة القدم العالمية", "https://images.example/365.jpg", "ملخص خبر 365Scores الرياضي للاختبار."),
      { status: 200, headers: { "content-type": "text/html" } }
    );
    if (url.includes("goal.com")) return new Response(
      article("https://www.goal.com/ar/news/test-goal-article", "جول العربية تنشر آخر أخبار كرة القدم", "https://images.example/goal.jpg", "ملخص خبر جول العربية للاختبار."),
      { status: 200, headers: { "content-type": "text/html" } }
    );
    if (url.includes("yallakora.com")) return new Response(
      article("https://www.yallakora.com/premier-league/news/573781/test", "يلا كورة تتابع أخبار الدوري الإنجليزي", "https://images.example/yalla.jpg", "ملخص خبر يلا كورة للاختبار."),
      { status: 200, headers: { "content-type": "text/html" } }
    );
    if (url.includes("kooora.com")) return new Response(
      article("https://www.kooora.com/news/test-kooora/123456", "كووورة تنشر خبرًا جديدًا عن الأندية العربية", "https://images.example/kooora.jpg", "ملخص خبر كووورة للاختبار."),
      { status: 200, headers: { "content-type": "text/html" } }
    );
    if (url === "https://mercatoday.com/") return new Response(
      article("https://mercatoday.com/2026/11/test-mercato/", "ميركاتو داي تتابع أحدث صفقات الانتقالات", "https://images.example/mercato.jpg", "ملخص خبر ميركاتو داي للاختبار."),
      { status: 200, headers: { "content-type": "text/html" } }
    );
    return new Response("", { status: 404 });
  };
  try {
    const response = await worker.fetch(new Request("https://koora.test/api/news?per_page=20"), {});
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.language, "ar");
    assert.equal(body.source, "publisher-websites");
    assert.equal(body.data.length, 5);
    assert.deepEqual(new Set(body.data.map((item) => item.source)), new Set(["365Scores", "GOAL العربية", "يلا كورة", "كووورة", "ميركاتو داي"]));
    assert.ok(body.data.every((item) => item.imageUrl?.startsWith("https://images.example/")));
    assert.equal(calls.length, 5);
    assert.ok(calls.every((url) => !url.includes("news.google.com")));
  } finally {
    globalThis.fetch = originalFetch;
    if (originalCaches === undefined) delete globalThis.caches;
    else globalThis.caches = originalCaches;
  }
});

test("latest transfers are normalized into KORAX transfer records", async () => {
  const originalFetch = globalThis.fetch;
  const originalCaches = globalThis.caches;
  globalThis.caches = { default: { match: async () => undefined, put: async () => undefined } };
  globalThis.fetch = async (input, init) => {
    assert.match(String(input), /transfers\/latest/);
    assert.equal(new Headers(init.headers).get("authorization"), "unit-test-token");
    return new Response(JSON.stringify({ data: [{
      id: 123, date: "2026-11-20", completed: true, amount: 2500000,
      player: { id: 77, name: "Player One", image_path: "https://images.example/player.png" },
      type: { id: 1, name: "Transfer" },
      fromTeam: { id: 10, name: "Old Club", image_path: "https://images.example/old.png" },
      toTeam: { id: 20, name: "New Club", image_path: "https://images.example/new.png" },
      league: { name: "Premier League" }
    }] }), { status: 200, headers: { "content-type": "application/json" } });
  };
  try {
    const response = await worker.fetch(new Request("https://koora.test/api/transfers?per_page=10"), { SPORTMONKS_API_TOKEN: "unit-test-token", ALLOWED_ORIGINS: "*" });
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.data[0].player.name, "Player One");
    assert.equal(body.data[0].fromTeam.name, "Old Club");
    assert.equal(body.data[0].toTeam.name, "New Club");
    assert.equal(body.data[0].typeName, "Transfer");
    assert.equal(body.data[0].completed, true);
  } finally {
    globalThis.fetch = originalFetch;
    if (originalCaches === undefined) delete globalThis.caches;
    else globalThis.caches = originalCaches;
  }
});
