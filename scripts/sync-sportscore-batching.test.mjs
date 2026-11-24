import test from "node:test";
import assert from "node:assert/strict";
import { DETAIL_BATCH_SIZE, selectDetailBatch } from "./sync-sportscore-batching.mjs";

test("uses four non-overlapping 20-item batches per hour for the standard 80 fixtures", () => {
  const fixtures = Array.from({ length: 80 }, (_, index) => `fixture-${index}`);
  const batches = [0, 15, 30, 45].map(minute => selectDetailBatch(fixtures, minute));
  assert.equal(DETAIL_BATCH_SIZE, 20);
  assert.deepEqual(batches.map(batch => batch.batchIndex), [0, 1, 2, 3]);
  assert.ok(batches.every(batch => batch.batchCount === 4 && batch.items.length === 20));
  assert.equal(new Set(batches.flatMap(batch => batch.items)).size, 80);
});

test("rotates beyond the first four batches across consecutive 15-minute sync cycles", () => {
  const fixtures = Array.from({ length: 95 }, (_, index) => `fixture-${index}`);
  const batches = Array.from({ length: 5 }, (_, cycle) => selectDetailBatch(fixtures, cycle * 15, 20, cycle));
  assert.deepEqual(batches.map(batch => batch.batchIndex), [0, 1, 2, 3, 4]);
  assert.deepEqual(batches.map(batch => batch.items.length), [20, 20, 20, 20, 15]);
  assert.equal(new Set(batches.flatMap(batch => batch.items)).size, 95);
});

test("normalizes negative and out-of-range cycle indices", () => {
  const fixtures = Array.from({ length: 80 }, (_, index) => index);
  assert.deepEqual(selectDetailBatch(fixtures, 0, 20, 75).items, selectDetailBatch(fixtures, 0, 20, 3).items);
  assert.deepEqual(selectDetailBatch(fixtures, 0, 20, -1).items, selectDetailBatch(fixtures, 0, 20, 3).items);
});

test("returns the final partial batch without duplicates", () => {
  const fixtures = Array.from({ length: 45 }, (_, index) => index);
  const batch = selectDetailBatch(fixtures, 30, 20);
  assert.equal(batch.batchIndex, 2);
  assert.deepEqual(batch.items, [40, 41, 42, 43, 44]);
});

test("rotates twelve live-detail slots without repeating a fixture before the cycle completes", () => {
  const live = Array.from({ length: 50 }, (_, index) => `live-${index}`);
  const batches = Array.from({ length: 5 }, (_, cycle) =>
    selectDetailBatch(live, cycle * 5, 12, cycle)
  );
  assert.deepEqual(batches.map(batch => batch.items.length), [12, 12, 12, 12, 2]);
  assert.equal(new Set(batches.flatMap(batch => batch.items)).size, 50);
});

test("uses eight-item rotating archive batches independently of live-detail cadence", () => {
  const archive = Array.from({ length: 37 }, (_, index) => `archive-${index}`);
  const batches = Array.from({ length: 5 }, (_, cycle) =>
    selectDetailBatch(archive, cycle * 15, 8, cycle)
  );
  assert.deepEqual(batches.map(batch => batch.items.length), [8, 8, 8, 8, 5]);
  assert.equal(new Set(batches.flatMap(batch => batch.items)).size, 37);
});

test("handles empty and non-array inputs safely", () => {
  assert.deepEqual(selectDetailBatch([], 0), { batchIndex: 0, batchCount: 1, items: [] });
  assert.throws(() => selectDetailBatch(null, 0), /items must be an array/);
});
