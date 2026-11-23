import test from "node:test";
import assert from "node:assert/strict";
import { DETAIL_BATCH_SIZE, selectDetailBatch } from "./sync-sportscore-batching.mjs";

test("uses four non-overlapping 20-item batches per hour", () => {
  const fixtures = Array.from({ length: 80 }, (_, index) => `fixture-${index}`);
  const batches = [0, 15, 30, 45].map(minute => selectDetailBatch(fixtures, minute));
  assert.equal(DETAIL_BATCH_SIZE, 20);
  assert.deepEqual(batches.map(batch => batch.batchIndex), [0, 1, 2, 3]);
  assert.ok(batches.every(batch => batch.batchCount === 4 && batch.items.length === 20));
  assert.equal(new Set(batches.flatMap(batch => batch.items)).size, 80);
});

test("returns the final partial batch without duplicates", () => {
  const fixtures = Array.from({ length: 45 }, (_, index) => index);
  const batch = selectDetailBatch(fixtures, 30, 20);
  assert.equal(batch.batchIndex, 2);
  assert.deepEqual(batch.items, [40, 41, 42, 43, 44]);
});

test("normalizes out-of-range minute values", () => {
  const fixtures = Array.from({ length: 80 }, (_, index) => index);
  assert.deepEqual(selectDetailBatch(fixtures, 75).items, selectDetailBatch(fixtures, 15).items);
  assert.deepEqual(selectDetailBatch(fixtures, -45).items, selectDetailBatch(fixtures, 15).items);
});

test("handles empty and non-array inputs safely", () => {
  assert.deepEqual(selectDetailBatch([], 0), { batchIndex: 0, batchCount: 1, items: [] });
  assert.throws(() => selectDetailBatch(null, 0), /items must be an array/);
});
