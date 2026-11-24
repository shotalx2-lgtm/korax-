import test from "node:test";
import assert from "node:assert/strict";
import { createJsonClient, ProviderHttpError } from "./http.mjs";

const response = (status, body, headers = {}) => ({
  ok: status >= 200 && status < 300,
  status,
  headers: { get: key => headers[key.toLowerCase()] ?? null },
  text: async () => body,
});

test("counts every HTTP attempt and enforces a provider request budget", async () => {
  const client = createJsonClient({
    source: "fixture",
    maxRequests: 2,
    fetchImpl: async () => response(200, '{"ok":true}'),
  });
  assert.deepEqual(await client("https://example.test/one"), { ok: true });
  assert.deepEqual(await client("https://example.test/two"), { ok: true });
  await assert.rejects(client("https://example.test/three"), error =>
    error instanceof ProviderHttpError && error.status === 429
  );
  assert.deepEqual(client.getStats(), { source: "fixture", requestCount: 2, maxRequests: 2, remaining: 0 });
});

test("retries a transient server error and returns the next valid response", async () => {
  let calls = 0;
  const client = createJsonClient({
    source: "transient",
    maxRetries: 1,
    fetchImpl: async () => (++calls === 1 ? response(503, "busy") : response(200, '{"ok":true}')),
  });
  assert.deepEqual(await client("https://example.test/retry"), { ok: true });
  assert.equal(calls, 2);
  assert.equal(client.getStats().requestCount, 2);
});

test("retries rate limiting only when Retry-After is short", async () => {
  let calls = 0;
  const client = createJsonClient({
    source: "limited",
    maxRetries: 1,
    fetchImpl: async () => (++calls === 1
      ? response(429, "slow down", { "retry-after": "0" })
      : response(200, '{"ok":true}')),
  });
  assert.deepEqual(await client("https://example.test/limited"), { ok: true });
  assert.equal(calls, 2);
});

test("does not retry permanent HTTP errors or malformed JSON", async () => {
  let calls = 0;
  const notFound = createJsonClient({
    source: "missing",
    fetchImpl: async () => { calls += 1; return response(404, "not found"); },
  });
  await assert.rejects(notFound("https://example.test/missing"), error =>
    error instanceof ProviderHttpError && error.status === 404 && !error.retryable
  );
  assert.equal(calls, 1);

  const malformed = createJsonClient({
    source: "malformed",
    fetchImpl: async () => response(200, "<html>"),
  });
  await assert.rejects(malformed("https://example.test/json"), /invalid JSON/);
});
