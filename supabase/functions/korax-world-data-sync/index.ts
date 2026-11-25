import { jwtVerify, createRemoteJWKSet } from "npm:jose@5.9.6";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") || "";
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
const OIDC_AUDIENCE = "korax-world-data-sync";
const ALLOWED_REPOSITORY = "shotalx2-lgtm/korax-";
const ALLOWED_WORKFLOW = "shotalx2-lgtm/korax-/.github/workflows/korax-world-data-sync.yml@refs/heads/main";
const JWKS = createRemoteJWKSet(new URL("https://token.actions.githubusercontent.com/.well-known/jwks"));

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
});

async function verifyWorkflowToken(request: Request) {
  const authorization = request.headers.get("authorization") || "";
  const token = authorization.replace(/^Bearer\s+/i, "").trim();
  if (!token) throw new Error("Missing GitHub Actions OIDC bearer token");

  const { payload } = await jwtVerify(token, JWKS, {
    issuer: "https://token.actions.githubusercontent.com",
    audience: OIDC_AUDIENCE,
  });
  const repository = String(payload.repository || "");
  const repositoryOwner = String(payload.repository_owner || "");
  const ref = String(payload.ref || "");
  const workflowRef = String(payload.workflow_ref || "");
  if (repository !== ALLOWED_REPOSITORY ||
      repositoryOwner !== "shotalx2-lgtm" ||
      ref !== "refs/heads/main" ||
      workflowRef !== ALLOWED_WORKFLOW) {
    throw new Error("GitHub Actions identity is not authorized for KORAX world-data sync");
  }
  return {
    repository,
    workflowRef,
    runId: String(payload.run_id || ""),
    sha: String(payload.sha || ""),
    eventName: String(payload.event_name || ""),
  };
}

async function rest(path: string, init: RequestInit = {}) {
  if (!SUPABASE_URL || !SERVICE_ROLE_KEY) throw new Error("Supabase service configuration is missing");
  const response = await fetch(SUPABASE_URL + path, {
    ...init,
    headers: {
      apikey: SERVICE_ROLE_KEY,
      authorization: `Bearer ${SERVICE_ROLE_KEY}`,
      "content-type": "application/json",
      ...(init.headers || {}),
    },
  });
  const body = await response.text();
  if (!response.ok) throw new Error(`Supabase REST ${response.status}: ${body.slice(0, 800)}`);
  return body ? JSON.parse(body) : null;
}

function array(value: unknown, max: number, label: string): any[] {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) throw new Error(`${label} must be an array`);
  if (value.length > max) throw new Error(`${label} exceeds the maximum batch size of ${max}`);
  return value;
}

async function upsertRows(table: string, rows: any[], conflict: string) {
  if (!rows.length) return 0;
  let processed = 0;
  for (let offset = 0; offset < rows.length; offset += 500) {
    const chunk = rows.slice(offset, offset + 500);
    await rest(`/rest/v1/${table}?on_conflict=${encodeURIComponent(conflict)}`, {
      method: "POST",
      headers: { Prefer: "resolution=merge-duplicates,return=minimal" },
      body: JSON.stringify(chunk),
    });
    processed += chunk.length;
  }
  return processed;
}

async function readState(payload: any) {
  const sourceKeys = array(payload.sourceKeys, 10, "sourceKeys")
    .map(value => String(value))
    .filter(value => /^[a-z0-9_]+$/.test(value));
  if (!sourceKeys.length) return json({ ok: true, coverage: [], runs: [] });
  const filter = encodeURIComponent(`in.(${sourceKeys.join(",")})`);
  const [coverage, runs] = await Promise.all([
    rest(`/rest/v1/korax_source_coverage?source_key=${filter}&select=source_key,entity_type,country_code,competition_name,season,coverage_status,record_count,last_checked_at,metadata`),
    rest(`/rest/v1/korax_source_sync_runs?source_key=${filter}&select=source_key,run_key,started_at,finished_at,status,metadata&order=started_at.desc&limit=200`),
  ]);
  return json({ ok: true, coverage: coverage || [], runs: runs || [] });
}

Deno.serve(async request => {
  if (request.method !== "POST") return json({ error: "METHOD_NOT_ALLOWED" }, 405);

  let identity;
  try {
    identity = await verifyWorkflowToken(request);
  } catch (error) {
    return json({ error: "UNAUTHORIZED", message: error instanceof Error ? error.message : String(error) }, 401);
  }

  let payload: any;
  try {
    const raw = await request.text();
    if (raw.length > 4_000_000) return json({ error: "PAYLOAD_TOO_LARGE" }, 413);
    payload = JSON.parse(raw);
  } catch {
    return json({ error: "INVALID_JSON" }, 400);
  }

  try {
    if (payload?.action === "state") return await readState(payload);

    if (payload?.action !== "sync") {
      return json({ error: "INVALID_ACTION", allowed: ["state", "sync"] }, 400);
    }

    const entities = array(payload.entities, 2_000, "entities");
    const entitySources = array(payload.entitySources, 4_000, "entitySources");
    const assets = array(payload.assets, 4_000, "assets");
    const facts = array(payload.facts, 8_000, "facts");
    const externalMatches = array(payload.externalMatches, 10_000, "externalMatches");
    const coverage = array(payload.coverage, 2_000, "coverage");
    const mergeCandidates = array(payload.mergeCandidates, 4_000, "mergeCandidates");
    const sourceRuns = array(payload.sourceRuns, 20, "sourceRuns");

    const counts = {
      entities: await upsertRows("korax_entities", entities, "id"),
      entitySources: await upsertRows("korax_entity_sources", entitySources, "source_key,external_id"),
      assets: await upsertRows("korax_entity_assets", assets, "entity_id,asset_type,asset_url"),
      facts: await upsertRows("korax_entity_facts", facts, "entity_id,field_key,source_key,source_external_id"),
      externalMatches: await upsertRows("korax_external_matches", externalMatches, "source_key,external_id"),
      coverage: await upsertRows("korax_source_coverage", coverage, "source_key,entity_type,country_code,competition_name,season"),
      mergeCandidates: await upsertRows("korax_entity_merge_candidates", mergeCandidates, "left_entity_id,right_entity_id"),
      sourceRuns: await upsertRows("korax_source_sync_runs", sourceRuns, "source_key,run_key"),
    };

    const healthBySource = new Map<string, any>();
    for (const run of sourceRuns) {
      if (!run?.source_key) continue;
      const current = healthBySource.get(run.source_key) || {};
      healthBySource.set(run.source_key, {
        health_status: run.status === "success" ? "healthy" : run.status === "partial" ? "degraded" : run.status === "failed" ? "offline" : run.status === "skipped" ? "disabled" : "unknown",
        last_success_at: run.status === "success" ? run.finished_at || new Date().toISOString() : current.last_success_at,
        last_error_at: run.status === "failed" || run.status === "partial" ? run.finished_at || new Date().toISOString() : null,
        last_error: run.error_summary || null,
        updated_at: new Date().toISOString(),
      });
    }
    for (const [sourceKey, health] of healthBySource.entries()) {
      await rest(`/rest/v1/korax_source_registry?source_key=eq.${encodeURIComponent(sourceKey)}`, {
        method: "PATCH",
        headers: { Prefer: "return=minimal" },
        body: JSON.stringify(health),
      });
    }

    return json({
      ok: true,
      identity: { repository: identity.repository, workflowRef: identity.workflowRef, runId: identity.runId, sha: identity.sha },
      counts,
    });
  } catch (error) {
    console.error("[korax-world-data-sync] failed", error);
    return json({ error: "WORLD_DATA_SYNC_FAILED", message: error instanceof Error ? error.message : String(error) }, 500);
  }
});
