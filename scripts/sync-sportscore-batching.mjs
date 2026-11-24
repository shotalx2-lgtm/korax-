export const DETAIL_BATCH_SIZE = 20;

/**
 * Select one deterministic batch from the rolling fixture-detail catalogue.
 * cycleIndex advances every 15 minutes, so catalogues larger than 80 fixtures
 * continue rotating instead of repeatedly fetching only the first four batches.
 */
export function selectDetailBatch(items, minute, batchSize = DETAIL_BATCH_SIZE, cycleIndex = null) {
  if (!Array.isArray(items)) throw new TypeError("items must be an array");
  const size = Number.isInteger(batchSize) && batchSize > 0 ? batchSize : DETAIL_BATCH_SIZE;
  const rawMinute = Number.isFinite(Number(minute)) ? Math.trunc(Number(minute)) : 0;
  const normalizedMinute = ((rawMinute % 60) + 60) % 60;
  const batchCount = Math.max(1, Math.ceil(items.length / size));
  const quarter = Math.floor(normalizedMinute / 15);
  const rawIndex = Number.isFinite(Number(cycleIndex)) ? Math.trunc(Number(cycleIndex)) : quarter;
  const batchIndex = ((rawIndex % batchCount) + batchCount) % batchCount;
  const start = batchIndex * size;
  return { batchIndex, batchCount, items: items.slice(start, start + size) };
}
