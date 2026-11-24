const DEFAULT_MAX_ROWS = 250;
const DEFAULT_MAX_BYTES = 1_500_000;

export function chunkRows(rows, options = {}) {
  const maxRows = Number.isInteger(options.maxRows) && options.maxRows > 0 ? options.maxRows : DEFAULT_MAX_ROWS;
  const maxBytes = Number.isInteger(options.maxBytes) && options.maxBytes > 256 ? options.maxBytes : DEFAULT_MAX_BYTES;
  if (!Array.isArray(rows)) throw new TypeError("Rows must be an array");
  const chunks = [];
  let current = [];
  let currentBytes = 2;
  for (const row of rows) {
    const rowBytes = Buffer.byteLength(JSON.stringify(row)) + 1;
    if (rowBytes + 2 > maxBytes) throw new Error("One row exceeds the maximum safe sync batch size");
    if (current.length && (current.length >= maxRows || currentBytes + rowBytes > maxBytes)) {
      chunks.push(current);
      current = [];
      currentBytes = 2;
    }
    current.push(row);
    currentBytes += rowBytes;
  }
  if (current.length) chunks.push(current);
  return chunks;
}

export function buildSyncBatches(payload, options = {}) {
  const order = [
    "entities",
    "entitySources",
    "assets",
    "facts",
    "externalMatches",
    "coverage",
    "mergeCandidates"
  ];
  const batches = [];
  for (const key of order) {
    const rows = Array.isArray(payload[key]) ? payload[key] : [];
    for (const chunk of chunkRows(rows, options)) batches.push({ [key]: chunk });
  }
  // Health and run status are written last, only after all data batches succeeded.
  batches.push({ sourceRuns: Array.isArray(payload.sourceRuns) ? payload.sourceRuns : [] });
  return batches;
}
