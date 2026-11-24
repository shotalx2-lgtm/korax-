export class ProviderHttpError extends Error {
  constructor(message, { source = "unknown", status = 0, url = "", retryable = false } = {}) {
    super(message);
    this.name = "ProviderHttpError";
    this.source = source;
    this.status = status;
    this.url = url;
    this.retryable = retryable;
  }
}

const sleep = ms => new Promise(resolve => setTimeout(resolve, Math.max(0, ms)));

function retryAfterMs(response) {
  const value = response.headers?.get?.("retry-after");
  if (!value) return 0;
  const seconds = Number(value);
  if (Number.isFinite(seconds)) return Math.max(0, seconds * 1000);
  const date = Date.parse(value);
  return Number.isFinite(date) ? Math.max(0, date - Date.now()) : 0;
}

export function createJsonClient({
  source,
  minIntervalMs = 0,
  maxRequests = 100,
  timeoutMs = 15_000,
  maxRetries = 2,
  fetchImpl = globalThis.fetch,
  defaultHeaders = {},
} = {}) {
  if (typeof fetchImpl !== "function") throw new TypeError("fetchImpl must be a function");
  let requestCount = 0;
  let lastRequestAt = 0;

  const getJson = async (url, options = {}) => {
    let lastError;
    for (let attempt = 0; attempt <= maxRetries; attempt += 1) {
      if (requestCount >= maxRequests) {
        throw new ProviderHttpError(`${source || "provider"} request budget reached (${maxRequests})`, {
          source, status: 429, url, retryable: false,
        });
      }
      const waitMs = minIntervalMs - (Date.now() - lastRequestAt);
      if (waitMs > 0) await sleep(waitMs);
      requestCount += 1;
      lastRequestAt = Date.now();

      let response;
      try {
        response = await fetchImpl(url, {
          headers: { accept: "application/json", ...defaultHeaders, ...(options.headers || {}) },
          signal: AbortSignal.timeout(options.timeoutMs || timeoutMs),
        });
      } catch (error) {
        lastError = new ProviderHttpError(String(error), { source, url, retryable: true });
        if (attempt < maxRetries) {
          await sleep(Math.min(4_000, 400 * (2 ** attempt)));
          continue;
        }
        throw lastError;
      }

      if (response.status === 429 || response.status >= 500) {
        const retryable = response.status === 429 || response.status >= 500;
        const body = await response.text().catch(() => "");
        lastError = new ProviderHttpError(
          `${source || "provider"} HTTP ${response.status}: ${body.slice(0, 240)}`,
          { source, status: response.status, url, retryable },
        );
        if (attempt < maxRetries) {
          const wait = response.status === 429 ? retryAfterMs(response) : 400 * (2 ** attempt);
          if (wait <= 30_000) {
            await sleep(Math.max(wait, 250));
            continue;
          }
        }
        throw lastError;
      }

      if (!response.ok) {
        const body = await response.text().catch(() => "");
        throw new ProviderHttpError(
          `${source || "provider"} HTTP ${response.status}: ${body.slice(0, 240)}`,
          { source, status: response.status, url, retryable: false },
        );
      }

      const body = await response.text();
      try {
        return JSON.parse(body);
      } catch {
        throw new ProviderHttpError(`${source || "provider"} returned invalid JSON`, {
          source, status: response.status, url, retryable: false,
        });
      }
    }
    throw lastError || new ProviderHttpError("Provider request failed", { source, url });
  };

  getJson.getStats = () => ({ source, requestCount, maxRequests, remaining: Math.max(0, maxRequests - requestCount) });
  return getJson;
}
