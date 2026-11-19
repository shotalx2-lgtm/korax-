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
    assert.equal(new Headers(init.headers).get("authorization"), "Bearer unit-test-token");
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
