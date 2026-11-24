import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createRemoteJWKSet, jwtVerify } from "npm:jose@5.9.6";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const GITHUB_OIDC_JWKS = createRemoteJWKSet(
  new URL("https://token.actions.githubusercontent.com/.well-known/jwks"),
);
const ALLOWED_GITHUB_REPOSITORY = "shotalx2-lgtm/korax-";
const ALLOWED_GITHUB_WORKFLOWS = new Set([
  "shotalx2-lgtm/korax-/.github/workflows/korax-sportscore-sync.yml@refs/heads/main",
]);

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
});

async function isAuthorizedGitHubRun(req: Request): Promise<boolean> {
  const authorization = req.headers.get("authorization") || "";
  const token = authorization.startsWith("Bearer ") ? authorization.slice(7) : "";
  if (!token) return false;
  try {
    const { payload } = await jwtVerify(token, GITHUB_OIDC_JWKS, {
      issuer: "https://token.actions.githubusercontent.com",
      audience: "korax-sportscore-sync",
      algorithms: ["RS256"],
    });
    return payload.repository === ALLOWED_GITHUB_REPOSITORY &&
      payload.ref === "refs/heads/main" &&
      ALLOWED_GITHUB_WORKFLOWS.has(String(payload.workflow_ref || ""));
  } catch (error) {
    console.error("[korax-sportscore-sync] GitHub OIDC validation failed", String(error));
    return false;
  }
}

async function rest(path: string, init: RequestInit = {}) {
  const response = await fetch(SUPABASE_URL + path, {
    ...init,
    headers: {
      apikey: SERVICE_KEY,
      Authorization: "Bearer " + SERVICE_KEY,
      Accept: "application/json",
      "content-type": "application/json",
      ...(init.headers || {}),
    },
  });
  const body = await response.text();
  if (!response.ok) throw new Error("Supabase REST HTTP " + response.status + ": " + body.slice(0, 500));
  return body ? JSON.parse(body) : null;
}

function parseIntOrNull(value: unknown): number | null {
  if (value === null || value === undefined || value === "") return null;
  const number = Number(value);
  return Number.isInteger(number) && number >= 0 ? number : null;
}

function matchSlug(match: any): string {
  const url = String(match?.url || match?.match_url || match?.href || "");
  const fromUrl = url.match(/\/match\/[^/]+\/([A-Za-z0-9_-]+)\/?(?:[?#].*)?$/i)?.[1] ||
    url.match(/\/([A-Za-z0-9_-]{8,})\/?(?:[?#].*)?$/)?.[1] || "";
  return String(match?.match_slug || match?.fixture_slug || match?.slug || fromUrl).trim();
}

function detailSlug(match: any): string {
  const url = String(match?.url || match?.match_url || match?.href || "");
  return String(match?.detail_slug || url.match(/\/match\/([^/]+)\/[A-Za-z0-9_-]+\/?(?:[?#].*)?$/i)?.[1] || matchSlug(match));
}

function mapMatch(match: any) {
  const slug = matchSlug(match);
  const home = typeof match?.home === "string" ? match.home : (match?.home?.name || match?.homeTeam?.name || match?.home_team?.name || "");
  const away = typeof match?.away === "string" ? match.away : (match?.away?.name || match?.awayTeam?.name || match?.away_team?.name || "");
  const kickoff = String(match?.time || match?.start_time || match?.startTime || match?.kickoff || match?.starting_at || "");
  const timestamp = Date.parse(kickoff);
  if (!slug || !home || !away || !Number.isFinite(timestamp)) return null;

  const status = String(match?.status || match?.state || "scheduled");
  const statusText = String(match?.status_text || match?.statusText || status);
  const statusValue = (status + " " + statusText).toLowerCase();
  const isFinished = /finished|full.?time|ended|complete|ft|بعد النهاية/.test(statusValue);
  const isLive = !isFinished && /live|in.?play|in_progress|1st.?half|2nd.?half|half.?time|extra.?time|penalt|الشوط|مباشر|استراحة|وقت إضافي/.test(statusValue);
  const competition = typeof match?.competition === "string"
    ? match.competition
    : (match?.competition?.name || match?.league?.name || match?.tournament?.name || match?.competition_name || match?.league_name || "");
  const competitionUrl = String(match?.competition?.url || match?.league?.url || "");
  const competitionSlug = String(match?.competition?.slug || match?.league?.slug ||
    competitionUrl.match(/\/([A-Za-z0-9_-]{8,})\/?$/)?.[1] || "");
  const homeImage = match?.home_logo || match?.home_image || match?.home?.image_path || match?.home?.imageUrl || match?.homeTeam?.imageUrl || "";
  const awayImage = match?.away_logo || match?.away_image || match?.away?.image_path || match?.away?.imageUrl || match?.awayTeam?.imageUrl || "";
  const competitionImage = match?.competition_logo || match?.competition_image || match?.competition?.image_path || match?.competition?.imageUrl || "";

  return {
    match_slug: slug,
    detail_slug: detailSlug(match),
    provider_id: String(match?.id || match?.fixture_id || match?.match_id || ""),
    provider_url: /^https:\/\//i.test(match?.url || "") ? match.url : (match?.url ? "https://sportscore.com" + match.url : ""),
    match_date: new Date(timestamp).toISOString().slice(0, 10),
    kickoff_at: new Date(timestamp).toISOString(),
    home_team: String(home),
    away_team: String(away),
    home_image: /^https:\/\//i.test(homeImage) ? homeImage : null,
    away_image: /^https:\/\//i.test(awayImage) ? awayImage : null,
    home_score: parseIntOrNull(match?.home_score ?? match?.homeScore?.current ?? match?.homeScore),
    away_score: parseIntOrNull(match?.away_score ?? match?.awayScore?.current ?? match?.awayScore),
    status,
    status_text: statusText,
    is_live: isLive,
    is_finished: isFinished,
    competition_name: String(competition),
    competition_slug: competitionSlug,
    competition_image: /^https:\/\//i.test(competitionImage) ? competitionImage : null,
    raw_fixture: match,
    updated_at: new Date().toISOString(),
  };
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);
  if (!(await isAuthorizedGitHubRun(req))) return json({ error: "UNAUTHORIZED_GITHUB_WORKFLOW" }, 401);
  let syncRunKey = "";
  let syncRunStartedAt = new Date().toISOString();
  let syncRequestCount = 0;
  try {
    const payload = await req.json();
    const workflowRunId = String(payload?.workflowRunId || "");
    const workflowRunAttempt = String(payload?.workflowRunAttempt || "1");
    syncRunKey = workflowRunId ? workflowRunId + ":" + workflowRunAttempt : String(payload?.syncedAt || syncRunStartedAt);
    syncRunStartedAt = String(payload?.startedAt || syncRunStartedAt);
    syncRequestCount = parseIntOrNull(payload?.requestCount) ?? 0;
    const matchesIn = Array.isArray(payload?.matches) ? payload.matches : [];
    const detailsIn = Array.isArray(payload?.details) ? payload.details : [];
    const competitionsIn = Array.isArray(payload?.competitions) ? payload.competitions : [];
    const recordsSeen = matchesIn.length + detailsIn.length + competitionsIn.length;
    await rest("/rest/v1/korax_source_sync_runs?on_conflict=source_key,run_key", {
      method: "POST",
      headers: { Prefer: "resolution=merge-duplicates,return=minimal" },
      body: JSON.stringify({
        source_key: "sportscore",
        workflow_run_id: workflowRunId || null,
        run_key: syncRunKey,
        status: "running",
        started_at: syncRunStartedAt,
        request_count: syncRequestCount,
        records_seen: recordsSeen,
        metadata: { workflow_run_attempt: workflowRunAttempt },
      }),
    });
    const deduped = new Map<string, any>();
    for (const source of matchesIn) {
      const row = mapMatch(source);
      if (row) deduped.set(row.match_slug, row);
    }
    const rows = [...deduped.values()];
    if (rows.length) {
      await rest("/rest/v1/korax_sportscore_matches?on_conflict=match_slug", {
        method: "POST",
        headers: { Prefer: "resolution=merge-duplicates,return=minimal" },
        body: JSON.stringify(rows),
      });
    }

    const detailRows = detailsIn.flatMap((entry: any) => {
      const slug = String(entry?.slug || matchSlug(entry?.detail || {}));
      if (!slug || !entry?.detail || typeof entry.detail !== "object") return [];
      return [{ match_slug: slug, detail: entry.detail, detail_updated_at: new Date().toISOString(), updated_at: new Date().toISOString() }];
    });
    if (detailRows.length) {
      await Promise.all(detailRows.map((row: any) =>
        rest("/rest/v1/korax_sportscore_matches?match_slug=eq." + encodeURIComponent(row.match_slug), {
          method: "PATCH",
          headers: { Prefer: "return=minimal" },
          body: JSON.stringify({
            detail: row.detail,
            detail_updated_at: row.detail_updated_at,
            updated_at: row.updated_at,
          }),
        })
      ));
    }

    const competitionRows = competitionsIn.flatMap((item: any) => {
      const slug = String(item?.slug || "").trim();
      if (!slug || !item?.standings) return [];
      return [{
        slug,
        name: String(item?.name || slug),
        country: item?.country ? String(item.country) : null,
        standings: item.standings,
        updated_at: new Date().toISOString(),
      }];
    });
    if (competitionRows.length) {
      await rest("/rest/v1/korax_sportscore_competitions?on_conflict=slug", {
        method: "POST",
        headers: { Prefer: "resolution=merge-duplicates,return=minimal" },
        body: JSON.stringify(competitionRows),
      });
    }

    const finishedAt = new Date().toISOString();
    const syncMetadata = {
      matches_upserted: rows.length,
      details_patched: detailRows.length,
      standings_upserted: competitionRows.length,
      received_matches: matchesIn.length,
      received_details: detailsIn.length,
      received_standings: competitionsIn.length,
      counts_are_processed_rows_not_insert_update_deltas: true,
    };
    await rest("/rest/v1/korax_source_sync_runs?source_key=eq.sportscore&run_key=eq." + encodeURIComponent(syncRunKey), {
      method: "PATCH",
      headers: { Prefer: "return=minimal" },
      body: JSON.stringify({
        status: "success",
        finished_at: finishedAt,
        records_skipped: Math.max(0, matchesIn.length - rows.length),
        metadata: syncMetadata,
      }),
    });
    await rest("/rest/v1/korax_source_registry?source_key=eq.sportscore", {
      method: "PATCH",
      headers: { Prefer: "return=minimal" },
      body: JSON.stringify({
        health_status: "healthy",
        last_success_at: finishedAt,
        last_error_at: null,
        last_error: null,
        updated_at: finishedAt,
      }),
    });

    return json({
      ok: true,
      received: { matches: matchesIn.length, details: detailsIn.length, competitions: competitionsIn.length },
      stored: { matches: rows.length, details: detailRows.length, competitions: competitionRows.length },
      requestCount: syncRequestCount,
      syncRunKey,
      updatedAt: finishedAt,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error("[korax-sportscore-sync] sync failed", message);
    if (syncRunKey) {
      const failedAt = new Date().toISOString();
      try {
        await rest("/rest/v1/korax_source_sync_runs?source_key=eq.sportscore&run_key=eq." + encodeURIComponent(syncRunKey), {
          method: "PATCH",
          headers: { Prefer: "return=minimal" },
          body: JSON.stringify({ status: "failed", finished_at: failedAt, request_count: syncRequestCount, error_count: 1, error_summary: message.slice(0, 1000) }),
        });
        await rest("/rest/v1/korax_source_registry?source_key=eq.sportscore", {
          method: "PATCH",
          headers: { Prefer: "return=minimal" },
          body: JSON.stringify({ health_status: "degraded", last_error_at: failedAt, last_error: message.slice(0, 1000), updated_at: failedAt }),
        });
      } catch (recordError) {
        console.error("[korax-sportscore-sync] unable to record failed run", String(recordError));
      }
    }
    return json({ error: "SYNC_FAILED", message }, 500);
  }
});
