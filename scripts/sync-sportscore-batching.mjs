export const DETAIL_BATCH_SIZE = 20;

/**
 * Divides today's fixture details into deterministic quarter-hour batches.
 * The standard 80-item cap is processed as four groups of at most 20.
 */
export function selectDetailBatch(items, minute, batchSize = DETAIL_BATCH_SIZE) {
  if (!Array.isArray(items)) throw new TypeError("items must be an array");
  const size = Number.isInteger(batchSize) && batchSize > 0 ? batchSize : DETAIL_BATCH_SIZE;
  const rawMinute = Number.isFinite(Number(minute)) ? Math.trunc(Number(minute)) : 0;
  const normalizedMinute = ((rawMinute % 60) + 60) % 60;
  const batchCount = Math.max(1, Math.ceil(items.length / size));
  const quarter = Math.floor(normalizedMinute / 15);
  const batchIndex = quarter % batchCount;
  const start = batchIndex * size;
  return { batchIndex, batchCount, items: items.slice(start, start + size) };
}
