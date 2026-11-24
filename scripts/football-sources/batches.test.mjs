import test from "node:test";
import assert from "node:assert/strict";
import { buildSyncBatches, chunkRows } from "./batches.mjs";

test("chunks rows by both count and byte size", () => {
  const rows = Array.from({ length: 501 }, (_, id) => ({ id, name: "x" }));
  const chunks = chunkRows(rows, { maxRows: 250, maxBytes: 1_000_000 });
  assert.deepEqual(chunks.map(chunk => chunk.length), [250, 250, 1]);
  assert.equal(chunks.flat().length, rows.length);
});

test("splits a batch before it crosses the byte budget", () => {
  const rows = [{ value: "a".repeat(800) }, { value: "b".repeat(800) }, { value: "c".repeat(800) }];
  const chunks = chunkRows(rows, { maxRows: 20, maxBytes: 1_000 });
  assert.equal(chunks.length, 3);
  for (const chunk of chunks) assert.ok(Buffer.byteLength(JSON.stringify(chunk)) <= 1_000);
});

test("rejects a single oversized row instead of sending an unsafe request", () => {
  assert.throws(() => chunkRows([{ value: "x".repeat(2_000) }], { maxBytes: 1_000 }), /exceeds/);
});

test("orders entity writes before foreign-key dependent rows and writes run health last", () => {
  const batches = buildSyncBatches({
    entities: [{ id: "e1" }],
    entitySources: [{ entity_id: "e1" }],
    assets: [{ entity_id: "e1" }],
    facts: [{ entity_id: "e1" }],
    externalMatches: [{ source_key: "openfootball" }],
    coverage: [{ source_key: "openfootball" }],
    mergeCandidates: [{ left_entity_id: "e1", right_entity_id: "e2" }],
    sourceRuns: [{ source_key: "wikidata", status: "success" }]
  });
  assert.deepEqual(batches.map(batch => Object.keys(batch)[0]), [
    "entities", "entitySources", "assets", "facts", "externalMatches", "coverage", "mergeCandidates", "sourceRuns"
  ]);
});

test("does not emit empty data batches and always keeps a final run-status batch", () => {
  const batches = buildSyncBatches({ entities: [], externalMatches: [], sourceRuns: [] });
  assert.deepEqual(batches, [{ sourceRuns: [] }]);
});
