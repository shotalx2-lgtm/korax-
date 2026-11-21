/**
 * KOORA API edge adapter.
 *
 * Provider credentials stay in Cloudflare Worker secrets. Never place them in
 * the Android application. Responses are normalized to the Android client
 * contract documented in docs/API_CONTRACT.md.
 */
const SPORTMONKS = "https://api.sportmonks.com/v3/football";

const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
  "x-content-type-options": "nosniff",
};

function json(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...JSON_HEADERS, ...headers },
  });
}

function corsHeaders(request, env) {
  const origin = request.headers.get("Origin") || "";
  const allowed = (env.ALLOWED_ORIGINS || "*")
    .split(",").map((value) => value.trim()).filter(Boolean);
  const permitted = allowed.includes("*") || allowed.includes(origin);
  return permitted ? {
    "access-control-allow-origin": allowed.includes("*") ? "*" : origin,
    "access-control-allow-methods": "GET, OPTIONS",
    "access-control-allow-headers": "Accept, Content-Type",
    "access-control-max-age": "86400",
    "vary": "Origin",
  } : {};
}

function unwrap(payload) {
  return payload && Object.prototype.hasOwnProperty.call(payload, "data")
    ? payload.data : payload;
}

function nestedName(value) {
  if (!value) return "";
  if (typeof value === "string") return value;
  if (typeof value === "object") return value.name || value.common_name || "";
  return "";
}

function participantLocation(participant) {
  return String(participant?.meta?.location || participant?.location || "").toLowerCase();
}

function normalizeParticipant(participant) {
  return {
    id: String(participant?.id ?? ""),
    name: participant?.name || "اسم غير متاح",
    crest: participant?.image_path || participant?.logoUrl || "",
    logoUrl: participant?.image_path || participant?.logoUrl || "",
    country: nestedName(participant?.country),
    venue: nestedName(participant?.venue),
  };
}

function normalizeScores(scores, homeId, awayId, stateName) {
  if (!Array.isArray(scores)) return { homeScore: null, awayScore: null };
  const finished = /finished|ft|full.?time|ended/i.test(String(stateName || ""));
  const preferred = finished
    ? ["CURRENT", "FT", "FULLTIME", "2ND_HALF"]
    : ["CURRENT", "1ST_HALF", "2ND_HALF", "EXTRA_TIME"];
  let chosen = null;
  for (const description of preferred) {
    chosen = scores.find((item) =>
      String(item?.description || "").toUpperCase() === description
    );
    if (chosen) break;
  }
  if (!chosen) chosen = scores[scores.length - 1];
  const home = scores.find((item) =>
    String(item?.participant_id ?? "") === String(homeId ?? "") &&
    String(item?.description || "").toUpperCase() === String(chosen?.description || "").toUpperCase()
  );
  const away = scores.find((item) =>
    String(item?.participant_id ?? "") === String(awayId ?? "") &&
    String(item?.description || "").toUpperCase() === String(chosen?.description || "").toUpperCase()
  );
  const read = (item) => item?.score?.goals ?? item?.score?.score ?? null;
  return {
    homeScore: read(home),
    awayScore: read(away),
    scoreType: chosen?.description || null,
  };
}

function normalizeEvent(event) {
  const type = nestedName(event?.type) || event?.type_name || event?.name || "حدث";
  const player = nestedName(event?.player) || event?.player_name || "";
  const team = nestedName(event?.participant) || nestedName(event?.team);
  return {
    minute: event?.minute ?? event?.extra_minute ?? event?.sort_order ?? "",
    type_name: type,
    player_id: event?.player?.id ?? event?.player_id ?? null,
    player_name: player,
    team_id: event?.participant?.id ?? event?.team?.id ?? event?.participant_id ?? event?.team_id ?? null,
    team: team ? { name: team } : null,
    detail: event?.addition || event?.info || event?.result || "",
  };
}

function normalizeLineup(item) {
  const player = item?.player || {};
  const team = item?.team || item?.participant || {};
  return {
    player_id: player?.id ?? item?.player_id ?? item?.id ?? null,
    player_name: nestedName(player) || item?.player_name || item?.name || "",
    imageUrl: player?.image_path || item?.imageUrl || "",
    position_name: nestedName(item?.position) || item?.position_name || "",
    shirt_number: item?.jersey_number ?? item?.shirt_number ?? null,
    team_id: team?.id ?? item?.team_id ?? null,
    team: team?.name ? { id: team.id ?? null, name: team.name } : null,
  };
}

function normalizeStatistic(item, teams = []) {
  const type = nestedName(item?.type) || item?.type_name || item?.name || "";
  const value = item?.data?.value ?? item?.value ?? item?.statistic ?? "";
  const team = teams.find((candidate) =>
    String(candidate.id) === String(item?.participant_id ?? item?.team_id ?? "")
  );
  return {
    name: type,
    value: value === null || value === undefined ? "" : String(value),
    team: team?.name || "",
  };
}

function normalizeFixture(fixture) {
  const participants = Array.isArray(fixture?.participants) ? fixture.participants : [];
  const homeRaw = participants.find((item) => participantLocation(item) === "home") || participants[0];
  const awayRaw = participants.find((item) => participantLocation(item) === "away") || participants[1];
  const home = normalizeParticipant(homeRaw || {});
  const away = normalizeParticipant(awayRaw || {});
  const state = fixture?.state || {};
  const stateName = state?.name || state?.short_name || state?.developer_name || "";
  const score = normalizeScores(fixture?.scores, home.id, away.id, stateName);
  const teams = [home, away];
  const events = Array.isArray(fixture?.events) ? fixture.events.map(normalizeEvent) : [];
  const lineups = Array.isArray(fixture?.lineups) ? fixture.lineups.map(normalizeLineup) : [];
  const statistics = Array.isArray(fixture?.statistics)
    ? fixture.statistics.map((item) => normalizeStatistic(item, teams)).filter((item) => item.name && item.value)
    : [];
  return {
    id: String(fixture?.id ?? ""),
    name: [home.name, away.name].filter(Boolean).join(" × "),
    homeTeam: home,
    awayTeam: away,
    home: home.name,
    away: away.name,
    homeScore: score.homeScore,
    awayScore: score.awayScore,
    scoreType: score.scoreType,
    status: stateName || "الحالة غير متاحة",
    startTime: fixture?.starting_at_timestamp
      ? new Date(Number(fixture.starting_at_timestamp) * 1000).toISOString()
      : fixture?.starting_at || "",
    competition: fixture?.league || fixture?.competition || null,
    competitionName: nestedName(fixture?.league || fixture?.competition),
    venue: fixture?.venue || null,
    referee: fixture?.referees?.[0] || null,
    events,
    lineups,
    statistics,
    imageUrl: "",
  };
}

function normalizeTeam(team) {
  return {
    id: String(team?.id ?? ""),
    name: team?.name || "اسم غير متاح",
    imageUrl: team?.image_path || "",
    logoUrl: team?.image_path || "",
    country: nestedName(team?.country),
    stadium: nestedName(team?.venue),
    founded: team?.founded ?? null,
    players: Array.isArray(team?.players) ? team.players.map((entry) => {
      const player = entry?.player || entry || {};
      return {
        id: String(player?.id ?? entry?.player_id ?? ""),
        name: player?.name || entry?.player_name || "",
        imageUrl: player?.image_path || "",
        position: nestedName(player?.position) || nestedName(entry?.position),
        shirt_number: entry?.jersey_number ?? entry?.shirt_number ?? player?.shirt_number ?? null,
      };
    }).filter((player) => player.id && player.name) : [],
    coaches: Array.isArray(team?.coaches) ? team.coaches : [],
    trophies: Array.isArray(team?.trophies) ? team.trophies : [],
  };
}

function normalizePlayer(player) {
  const activeTeam = Array.isArray(player?.teams)
    ? player.teams.find((item) => item?.active) || player.teams[0]
    : null;
  return {
    id: String(player?.id ?? ""),
    name: player?.name || "اسم غير متاح",
    imageUrl: player?.image_path || "",
    position: nestedName(player?.position) || nestedName(player?.detailed_position),
    nationality: nestedName(player?.nationality) || nestedName(player?.country),
    date_of_birth: player?.date_of_birth || "",
    age: player?.age ?? null,
    height: player?.height ?? null,
    weight: player?.weight ?? null,
    shirt_number: player?.shirt_number ?? null,
    team: activeTeam?.team || activeTeam?.name || null,
    statistics: Array.isArray(player?.statistics) ? player.statistics : [],
    transfers: Array.isArray(player?.transfers) ? player.transfers : [],
  };
}

function normalizeTransfer(item) {
  const player = item?.player || {};
  const from = item?.fromTeam || item?.from_team || {};
  const to = item?.toTeam || item?.to_team || {};
  const league = item?.league || from?.league || to?.league || null;
  return {
    id: String(item?.id ?? ""),
    player: { id: String(player?.id ?? item?.player_id ?? ""), name: player?.name || item?.player_name || "اسم غير متاح", image_path: player?.image_path || "", imageUrl: player?.image_path || "" },
    playerName: player?.name || item?.player_name || "",
    fromTeam: { id: String(from?.id ?? item?.from_team_id ?? ""), name: from?.name || "", image_path: from?.image_path || "", imageUrl: from?.image_path || "" },
    toTeam: { id: String(to?.id ?? item?.to_team_id ?? ""), name: to?.name || "", image_path: to?.image_path || "", imageUrl: to?.image_path || "" },
    fromTeamName: from?.name || "", toTeamName: to?.name || "",
    type: item?.type || null, typeName: nestedName(item?.type) || item?.type_name || "",
    typeId: item?.type_id ?? null, date: item?.date || item?.completed_at || "",
    completed: Boolean(item?.completed), amount: item?.amount ?? null, league, leagueName: nestedName(league),
  };
}

function normalizeLeague(league) {
  return {
    id: String(league?.id ?? ""),
    name: league?.name || "اسم غير متاح",
    imageUrl: league?.image_path || "",
    logoUrl: league?.image_path || "",
    country: nestedName(league?.country),
    type: league?.type || "",
    currentSeason: league?.currentseason || league?.currentSeason || null,
  };
}

async function providerRequest(path, params, env) {
  if (!env.SPORTMONKS_API_TOKEN) {
    const error = new Error("SPORTMONKS_API_TOKEN is not configured on the server");
    error.status = 503;
    throw error;
  }
  const url = new URL(SPORTMONKS + path);
  for (const [key, value] of Object.entries(params || {})) {
    if (value !== undefined && value !== null && value !== "") url.searchParams.set(key, String(value));
  }
  const response = await fetch(url, {
    headers: {
      "accept": "application/json",
      "Authorization": env.SPORTMONKS_API_TOKEN,
    },
    signal: AbortSignal.timeout(12000),
  });
  if (!response.ok) {
    const error = new Error("Football data provider returned " + response.status);
    error.status = response.status === 429 ? 503 : response.status;
    throw error;
  }
  return response.json();
}

async function cachedProviderRequest(path, params, env, ttlSeconds) {
  const cache = caches.default;
  const keyUrl = new URL("https://koora-cache.invalid" + path);
  for (const [key, value] of Object.entries(params || {})) {
    if (value !== undefined && value !== null && value !== "") keyUrl.searchParams.set(key, String(value));
  }
  const cacheKey = new Request(keyUrl.toString(), { method: "GET" });
  const cached = await cache.match(cacheKey);
  if (cached) return cached.json();
  const payload = await providerRequest(path, params, env);
  const response = new Response(JSON.stringify(payload), {
    headers: { "content-type": "application/json", "cache-control": "public, max-age=" + ttlSeconds },
  });
  await cache.put(cacheKey, response);
  return payload;
}

function getList(payload) {
  const data = unwrap(payload);
  return Array.isArray(data) ? data : [];
}

function getOne(payload) {
  const data = unwrap(payload);
  return Array.isArray(data) ? data[0] || null : data || null;
}


// KORAX news is collected directly from the Arabic sports publishers below.
// No Sportmonks token, paid news API, or Google News feed is used for this endpoint.
const NEWS_WEBSITES = [
  {
    id: "365scores",
    source: "365Scores",
    url: "https://www.365scores.com/ar/news/magazine/",
    accepts: (path) => path.startsWith("/ar/news/magazine/") && path.length > "/ar/news/magazine/".length,
  },
  {
    id: "goal",
    source: "GOAL العربية",
    url: "https://www.goal.com/ar/أخبار",
    accepts: (path) => path.startsWith("/ar/") && path.length > 5 && !/\/(أخبار|news|live-scores|transfers)\/?$/i.test(path),
  },
  {
    id: "yallakora",
    source: "يلا كورة",
    url: "https://www.yallakora.com/newslisting",
    accepts: (path) => /\/news\/\d+/i.test(path),
  },
  {
    id: "kooora",
    source: "كووورة",
    url: "https://www.kooora.com/news",
    accepts: (path) => path !== "/news" && (path.includes("/news/") || /\/\d{5,}\/?$/.test(path)),
  },
  {
    id: "mercatoday",
    source: "ميركاتو داي",
    url: "https://mercatoday.com/",
    rssFallback: "https://mercatoday.com/feed/",
    accepts: (path) => path !== "/" && !/\/(category|tag|author|feed|wp-json)(\/|$)/i.test(path),
  },
];

function decodeXml(value) {
  return String(value || "")
    .replaceAll("<![CDATA[", "")
    .replaceAll("]]>", "")
    .replace(/&#x([0-9a-f]+);/gi, (_, code) => String.fromCodePoint(parseInt(code, 16)))
    .replace(/&#([0-9]+);/g, (_, code) => String.fromCodePoint(Number(code)))
    .replace(/&nbsp;/gi, " ")
    .replace(/&quot;/gi, '"')
    .replace(/&apos;/gi, "'")
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">")
    .replace(/&amp;/gi, "&");
}
function decodeHtml(value) {
  return decodeXml(String(value || ""))
    .replace(/&nbsp;/gi, " ")
    .replace(/&#x([0-9a-f]+);/gi, (_, code) => String.fromCodePoint(parseInt(code, 16)))
    .replace(/&#([0-9]+);/g, (_, code) => String.fromCodePoint(Number(code)))
    .replace(/&apos;/gi, "'")
    .replace(/&#39;/gi, "'")
    .replace(/&#34;/gi, '"');
}

function htmlText(value) {
  return decodeHtml(String(value || "")
    .replace(/<script\b[\s\S]*?<\/script>/gi, " ")
    .replace(/<style\b[\s\S]*?<\/style>/gi, " ")
    .replace(/<noscript\b[\s\S]*?<\/noscript>/gi, " ")
    .replace(/<!--[\s\S]*?-->/g, " ")
    .replace(/<[^>]+>/g, " "))
    .replace(/\s+/g, " ")
    .trim();
}

function htmlAttribute(attributes, name) {
  const escaped = name.replace(/[.*+?^$()|[\]\\]/g, "\\$&");
  const match = String(attributes || "").match(new RegExp("\\b" + escaped + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", "i"));
  return decodeHtml(match ? (match[1] ?? match[2] ?? match[3] ?? "") : "");
}

function absoluteHttps(rawUrl, baseUrl) {
  if (!rawUrl) return "";
  try {
    const url = new URL(rawUrl, baseUrl);
    if (url.protocol !== "https:") return "";
    return url.toString();
  } catch {
    return "";
  }
}

function rssValue(block, name) {
  const escapedName = name.replace(/:/g, "\\:");
  const match = String(block || "").match(new RegExp(
    "<(?:[\\w.-]+:)?" + escapedName + "\\b[^>]*>([\\s\\S]*?)<\\/(?:[\\w.-]+:)?" + escapedName + "\\s*>",
    "i"
  ));
  return match ? htmlText(match[1]) : "";
}

function parseArabicRss(xml, source) {
  const blocks = String(xml || "").match(/<(?:item|entry)\\b[\\s\\S]*?<\\/(?:item|entry)>/gi) || [];
  return blocks.slice(0, 40).map((block) => {
    const title = rssValue(block, "title");
    const summary = (rssValue(block, "description") || rssValue(block, "summary")).slice(0, 300);
    const rawLink = rssValue(block, "link") || rssValue(block, "guid");
    const url = absoluteHttps(rawLink, "https://mercatoday.com/");
    const imageMatch = block.match(/<(?:media:content|media:thumbnail|enclosure)\\b[^>]*\\burl=["']([^"']+)["']/i)
      || block.match(/<img\\b[^>]*\\bsrc=["']([^"']+)["']/i);
    const imageUrl = absoluteHttps(imageMatch?.[1] || "", "https://mercatoday.com/");
    const publishedAt = rssValue(block, "pubDate") || rssValue(block, "published") || rssValue(block, "updated");
    if (!title || !/[\\u0600-\\u06FF]/.test(title) || !url) return null;
    return { id: url, title, summary, url, source, publishedAt, imageUrl: imageUrl || null };
  }).filter(Boolean);
}

function articleImage(innerHtml, baseUrl, pageHtml) {
  const imgTags = String(innerHtml || "").match(/<img\b[^>]*>/gi) || [];
  for (const tag of imgTags) {
    const candidate =
      htmlAttribute(tag, "data-src") ||
      htmlAttribute(tag, "data-lazy-src") ||
      htmlAttribute(tag, "data-original") ||
      htmlAttribute(tag, "src") ||
      htmlAttribute(tag, "srcset").split(",")[0]?.trim().split(/\s+/)[0];
    const resolved = absoluteHttps(candidate, baseUrl);
    if (resolved) return resolved;
  }
  const og = String(pageHtml || "").match(/<meta\b[^>]*(?:property|name)=["'](?:og:image|twitter:image)["'][^>]*content=["']([^"']+)["']/i)
    || String(pageHtml || "").match(/<meta\b[^>]*content=["']([^"']+)["'][^>]*(?:property|name)=["'](?:og:image|twitter:image)["']/i);
  return absoluteHttps(og?.[1] || "", baseUrl) || null;
}

function parseWebsiteNews(pageHtml, site) {
  const base = new URL(site.url);
  const anchors = String(pageHtml || "").match(/<a\b[^>]*>[\s\S]*?<\/a>/gi) || [];
  const output = [];
  const seen = new Set();

  for (const anchor of anchors) {
    const opening = anchor.match(/^<a\b([^>]*)>/i);
    if (!opening) continue;
    const href = htmlAttribute(opening[1], "href");
    const url = absoluteHttps(href, site.url);
    if (!url) continue;

    let parsed;
    try { parsed = new URL(url); } catch { continue; }
    if (parsed.hostname !== base.hostname && !parsed.hostname.endsWith("." + base.hostname)) continue;
    if (!site.accepts(parsed.pathname)) continue;

    const inner = anchor.slice(opening[0].length).replace(/<\/a>\s*$/i, "");
    const heading = inner.match(/<h[1-6]\b[^>]*>([\s\S]*?)<\/h[1-6]>/i);
    const imageTag = inner.match(/<img\b([^>]*)>/i);
    const title =
      htmlText(heading?.[1] || "") ||
      htmlAttribute(opening[1], "aria-label") ||
      htmlAttribute(opening[1], "title") ||
      htmlAttribute(imageTag?.[1], "alt") ||
      htmlText(inner);

    if (title.length < 16 || title.length > 220 || !/[\u0600-\u06FF]/.test(title)) continue;
    if (/^(المزيد|عرض الكل|أخبار|مباريات اليوم|تابعنا|الرئيسية|انتقالات)$/i.test(title)) continue;
    if (seen.has(url)) continue;
    seen.add(url);

    const paragraphs = [...inner.matchAll(/<p\b[^>]*>([\s\S]*?)<\/p>/gi)]
      .map((match) => htmlText(match[1]))
      .filter((value) => value.length > 30 && value !== title);
    const summary = (paragraphs[0] || htmlText(inner).replace(title, "").trim()).slice(0, 300);

    const timeMatch = inner.match(/<time\b[^>]*datetime=["']([^"']+)["']/i);
    const dateMatch = inner.match(/\b(?:20\d{2}[-/]\d{1,2}[-/]\d{1,2}|\d{1,2}[-/]\d{1,2}[-/]\d{4})\b/);
    const publishedAt = timeMatch?.[1] || dateMatch?.[0] || "";

    output.push({
      id: url,
      title,
      summary,
      url,
      source: site.source,
      publishedAt,
      imageUrl: articleImage(inner, site.url, pageHtml),
    });
  }
  return output.slice(0, 35);
}

async function fetchNewsWebsite(site) {
  const headers = {
    "accept": "text/html,application/xhtml+xml,application/rss+xml,application/xml;q=0.9,*/*;q=0.8",
    "user-agent": "Mozilla/5.0 (compatible; KORAX-News/1.0; +https://github.com/shotalx2-lgtm/korax-)",
    "accept-language": "ar,en;q=0.8",
  };

  try {
    const response = await fetch(site.url, {
      headers,
      redirect: "follow",
      signal: AbortSignal.timeout(11000),
    });
    if (!response.ok) throw new Error("Publisher returned HTTP " + response.status);
    const html = await response.text();
    const articles = parseWebsiteNews(html, site);
    if (articles.length) return articles;
    if (!site.rssFallback) return [];
  } catch (error) {
    if (!site.rssFallback) throw error;
  }

  if (site.rssFallback) {
    const response = await fetch(site.rssFallback, {
      headers: { ...headers, "accept": "application/rss+xml,application/xml,text/xml,*/*" },
      signal: AbortSignal.timeout(9000),
    });
    if (!response.ok) throw new Error("Publisher RSS returned HTTP " + response.status);
    return parseArabicRss(await response.text(), site.source);
  }
  return [];
}

async function loadArabicNews() {
  const responses = await Promise.allSettled(NEWS_WEBSITES.map(fetchNewsWebsite));
  const articles = responses.flatMap((result) => result.status === "fulfilled" ? result.value : []);
  const unique = new Map();
  for (const article of articles) {
    const normalizedTitle = article.title.toLocaleLowerCase("ar")
      .replace(/[^\p{L}\p{N}]+/gu, " ")
      .trim();
    const key = article.url || normalizedTitle;
    if (!unique.has(key) && normalizedTitle) unique.set(key, article);
  }
  return Array.from(unique.values())
    .sort((a, b) => (Date.parse(b.publishedAt) || 0) - (Date.parse(a.publishedAt) || 0))
    .slice(0, 60);
}

async function getCachedArabicNews() {
  const cacheKey = new Request("https://korax-cache.invalid/api/news?language=ar&source=publisher-websites-v1");
  const cache = caches.default;
  const cached = await cache.match(cacheKey);
  if (cached) return cached.json();
  const payload = { data: await loadArabicNews(), language: "ar", updatedAt: new Date().toISOString() };
  await cache.put(cacheKey, new Response(JSON.stringify(payload), {
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "public, max-age=180" },
  }));
  return payload;
}

async function route(request, env) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "") || "/";
  const perPage = Math.min(50, Math.max(1, Number(url.searchParams.get("per_page") || 30)));
  const page = Math.max(1, Number(url.searchParams.get("page") || 1));

  if (path === "/api/news") {
    const payload = await getCachedArabicNews();
    const requested = Math.min(60, Math.max(1, Number(url.searchParams.get("per_page") || 40)));
    return json({ ...payload, data: payload.data.slice(0, requested), source: "publisher-websites" });
  }

  if (path === "/health") {
    return json({ ok: true, service: "KOORA API", providerConfigured: Boolean(env.SPORTMONKS_API_TOKEN) });
  }

  if (path === "/api/matches") {
    const live = url.searchParams.get("live") === "1";
    const date = url.searchParams.get("date") || new Date().toISOString().slice(0, 10);
    if (!live && !/^\d{4}-\d{2}-\d{2}$/.test(date)) {
      return json({ error: "date must use YYYY-MM-DD" }, 400);
    }
    const providerPath = live ? "/livescores/inplay" : "/fixtures/date/" + date;
    const payload = await cachedProviderRequest(providerPath, {
      include: "participants;league;state;scores",
      per_page: perPage,
      page,
    }, env, live ? 10 : 45);
    return json({
      data: getList(payload).map(normalizeFixture),
      meta: payload?.meta || null,
      source: "sportmonks",
      updatedAt: new Date().toISOString(),
    });
  }

  const match = path.match(/^\/api\/matches\/([A-Za-z0-9_-]+)$/);
  if (match) {
    const payload = await cachedProviderRequest("/fixtures/" + encodeURIComponent(match[1]), {
      include: "participants;league;state;scores;events.type;lineups.details.type;statistics.type;venue;referees;weatherReport",
    }, env, 20);
    const fixture = getOne(payload);
    return fixture ? json({ data: normalizeFixture(fixture), source: "sportmonks" }) : json({ error: "Match not found" }, 404);
  }

  if (path === "/api/teams") {
    const payload = await cachedProviderRequest("/teams", {
      include: "country;venue",
      per_page: perPage,
      page,
      search: url.searchParams.get("search") || undefined,
    }, env, 1800);
    return json({ data: getList(payload).map(normalizeTeam), meta: payload?.meta || null, source: "sportmonks" });
  }

  const team = path.match(/^\/api\/teams\/([A-Za-z0-9_-]+)$/);
  if (team) {
    const payload = await cachedProviderRequest("/teams/" + encodeURIComponent(team[1]), {
      include: "country;venue;coaches;players;statistics;trophies;sidelined",
    }, env, 600);
    const item = getOne(payload);
    return item ? json({ data: normalizeTeam(item), source: "sportmonks" }) : json({ error: "Team not found" }, 404);
  }

  if (path === "/api/players") {
    const search = url.searchParams.get("search");
    const providerPath = search ? "/players/search/" + encodeURIComponent(search) : "/players";
    const payload = await cachedProviderRequest(providerPath, {
      include: "country;nationality;position;teams",
      per_page: perPage,
      page,
    }, env, 1800);
    return json({ data: getList(payload).map(normalizePlayer), meta: payload?.meta || null, source: "sportmonks" });
  }

  const player = path.match(/^\/api\/players\/([A-Za-z0-9_-]+)$/);
  if (player) {
    const payload = await cachedProviderRequest("/players/" + encodeURIComponent(player[1]), {
      include: "country;nationality;position;teams;statistics;transfers;trophies",
    }, env, 600);
    const item = getOne(payload);
    return item ? json({ data: normalizePlayer(item), source: "sportmonks" }) : json({ error: "Player not found" }, 404);
  }

  if (path === "/api/transfers") {
    const payload = await cachedProviderRequest("/transfers/latest", {
      include: "player;type;fromTeam;toTeam;position;detailedPosition",
      order: "desc", per_page: perPage, page,
    }, env, 900);
    return json({
      data: getList(payload).map(normalizeTransfer),
      meta: payload?.meta || null,
      source: "sportmonks",
      updatedAt: new Date().toISOString(),
    });
  }

  if (path === "/api/competitions") {
    const payload = await cachedProviderRequest("/leagues", {
      include: "country;currentSeason",
      per_page: perPage,
      page,
    }, env, 3600);
    return json({ data: getList(payload).map(normalizeLeague), meta: payload?.meta || null, source: "sportmonks" });
  }

  const competition = path.match(/^\/api\/competitions\/([A-Za-z0-9_-]+)$/);
  if (competition) {
    const payload = await cachedProviderRequest("/leagues/" + encodeURIComponent(competition[1]), {
      include: "country;currentSeason;seasons",
    }, env, 1800);
    const item = getOne(payload);
    return item ? json({ data: normalizeLeague(item), source: "sportmonks" }) : json({ error: "Competition not found" }, 404);
  }

  return json({ error: "Not found" }, 404);
}

export default {
  async fetch(request, env) {
    const cors = corsHeaders(request, env);
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    if (request.method !== "GET") return json({ error: "Method not allowed" }, 405, cors);
    try {
      const response = await route(request, env);
      const headers = new Headers(response.headers);
      for (const [key, value] of Object.entries(cors)) headers.set(key, value);
      return new Response(response.body, { status: response.status, headers });
    } catch (error) {
      const status = Number(error?.status || 502);
      return json({
        error: status === 503 ? "Football data service is unavailable or not configured" : "Unable to retrieve football data",
        retryable: status === 429 || status >= 500,
      }, status >= 400 && status <= 599 ? status : 502, cors);
    }
  },
};
