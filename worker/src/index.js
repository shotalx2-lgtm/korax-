/**
 * KOORA API edge adapter.
 *
 * Provider credentials stay in Cloudflare Worker secrets. Never place them in
 * the Android application. Responses are normalized to the Android client
 * contract documented in docs/API_CONTRACT.md.
 */
const SPORTSCORE = "https://sportscore.com";

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

function httpsImage(...values) {
  for (const value of values) {
    if (typeof value === "string" && /^https:\/\//i.test(value)) return value;
  }
  return "";
}

function normalizeEvent(event) {
  const type = nestedName(event?.type) || event?.type_name || event?.name || "حدث";
  const playerRaw = event?.player && typeof event.player === "object" ? event.player : {};
  const player = nestedName(event?.player) || event?.player_name || "";
  const teamRaw = event?.participant || event?.team || {};
  const team = nestedName(teamRaw);
  const assistRaw = event?.assist || event?.assist_player || event?.assisted_by ||
    event?.assistPlayer || event?.assist_player_info || {};
  const assistName = nestedName(assistRaw) || event?.assist_player_name || event?.assist_name || "";
  const teamImage = httpsImage(teamRaw?.image_path, teamRaw?.image_url, teamRaw?.logo);
  return {
    minute: event?.minute ?? event?.extra_minute ?? event?.sort_order ?? "",
    type_name: type,
    player_id: playerRaw?.id ?? event?.player_id ?? null,
    player_name: player,
    player_image_url: httpsImage(playerRaw?.image_path, playerRaw?.image_url, playerRaw?.photo_url),
    team_id: teamRaw?.id ?? event?.participant_id ?? event?.team_id ?? null,
    team: team ? { id: teamRaw?.id ?? null, name: team, imageUrl: teamImage } : null,
    team_image_url: teamImage,
    assist_player_name: assistName,
    assist_player_image_url: httpsImage(assistRaw?.image_path, assistRaw?.image_url, assistRaw?.photo_url),
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

function fixtureRoot(fixture) {
  if (fixture?.match && typeof fixture.match === "object") return fixture.match;
  if (fixture?.fixture && typeof fixture.fixture === "object") return fixture.fixture;
  return fixture || {};
}

function fixtureStatusText(fixture) {
  const root = fixtureRoot(fixture);
  const status = root.status || root.state || fixture?.status || fixture?.state || "";
  return String(root.status_text || nestedName(status) ||
    (typeof status === "string" ? status : status?.description || status?.short_name) || "");
}

function isLiveFixture(fixture) {
  return /live|in.?play|in_progress|1st.?half|2nd.?half|half.?time|\bht\b|\b1h\b|\b2h\b|extra.?time|penalt|الشوط|مباشر|استراحة|وقت إضافي|ركلات ترجيح/i
    .test(fixtureStatusText(fixture));
}

function normalizeFixture(fixture) {
  const root = fixtureRoot(fixture);
  const teams = root.teams || fixture?.teams || {};
  const participants = Array.isArray(root.participants) ? root.participants
    : Array.isArray(fixture?.participants) ? fixture.participants : [];
  const homeRaw = root.home_team || root.homeTeam ||
    (root.home && typeof root.home === "object" ? root.home : null) ||
    teams.home || participants.find((item) => participantLocation(item).includes("home")) || {};
  const awayRaw = root.away_team || root.awayTeam ||
    (root.away && typeof root.away === "object" ? root.away : null) ||
    teams.away || participants.find((item) => participantLocation(item).includes("away")) || {};
  const toTeam = (raw, fallbackName, logo) => {
    const obj = raw && typeof raw === "object" ? raw : {};
    const name = nestedName(raw) || (typeof raw === "string" ? raw : "") ||
      fallbackName || "اسم غير متاح";
    const image = obj.image_path || obj.logo || obj.logo_url || obj.image || obj.image_url || logo || "";
    const imageUrl = typeof image === "string" && /^https:\/\//i.test(image) ? image : "";
    return {
      id: String(obj.id ?? obj.slug ?? ""),
      name,
      crest: imageUrl,
      logoUrl: imageUrl,
      country: nestedName(obj.country),
    };
  };
  const home = toTeam(homeRaw,
    typeof root.home === "string" ? root.home : (root.home_name || root.home_team_name || ""),
    root.home_logo || root.home_image);
  const away = toTeam(awayRaw,
    typeof root.away === "string" ? root.away : (root.away_name || root.away_team_name || ""),
    root.away_logo || root.away_image);
  const score = root.score && typeof root.score === "object" ? root.score : {};
  const statusName = fixtureStatusText(fixture) || "الحالة غير متاحة";
  const nestedScores = normalizeScores(root.scores, home.id, away.id, statusName);
  const homeScore = firstScore(root.home_score, root.homeScore, score.home,
    score.home_score, nestedScores.homeScore);
  const awayScore = firstScore(root.away_score, root.awayScore, score.away,
    score.away_score, nestedScores.awayScore);
  const competition = root.competition || root.league || root.tournament ||
    root.competition_name || root.league_name || null;
  const competitionObject = typeof competition === "string" ? { name: competition }
    : competition && typeof competition === "object" ? { ...competition } : null;
  if (competitionObject) {
    competitionObject.imageUrl = httpsImage(
      competitionObject.image_path, competitionObject.image_url,
      competitionObject.logo, competitionObject.logo_url, competitionObject.image
    );
  }
  const eventsRaw = Array.isArray(root.events) ? root.events
    : Array.isArray(root.timeline) ? root.timeline : [];
  const lineupRaw = Array.isArray(root.lineups) ? root.lineups
    : Array.isArray(root.lineup) ? root.lineup : [];
  const statisticRaw = Array.isArray(root.statistics) ? root.statistics
    : Array.isArray(root.stats) ? root.stats : [];
  const rawId = root.slug || root.match_slug || root.id || root.fixture_id ||
    root.match_id || root.fixture_slug || fixture?.slug || fixture?.match_slug ||
    fixture?.id || fixture?.fixture_id || fixture?.match_id || "";
  const rawTime = root.time || root.start_time || root.startTime || root.kickoff ||
    root.date || root.starting_at || root.start_at || "";
  return {
    id: String(rawId),
    name: [home.name, away.name].filter(Boolean).join(" × "),
    homeTeam: home, awayTeam: away, home: home.name, away: away.name,
    homeScore, awayScore, scoreType: nestedScores.scoreType || null, status: statusName,
    startTime: String(rawTime),
    competition: competitionObject,
    competitionName: nestedName(competition) || (typeof competition === "string" ? competition : ""),
    venue: root.venue || root.stadium || null,
    referee: root.referee || null,
    events: eventsRaw.map(normalizeEvent),
    lineups: lineupRaw.map(normalizeLineup),
    statistics: statisticRaw.map((item) => normalizeStatistic(item, [home, away])),
    imageUrl: root.imageUrl || root.image_url || "",
  };
}

function firstScore(...values) {
  for (const value of values) {
    if (value === null || value === undefined || value === "") continue;
    const number = Number(value);
    if (Number.isFinite(number)) return number;
  }
  return null;
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

async function providerRequest(path, params = {}) {
  const url = new URL(path, SPORTSCORE);
  for (const [key, value] of Object.entries(params || {})) {
    if (value !== undefined && value !== null && value !== "") url.searchParams.set(key, String(value));
  }
  const response = await fetch(url, {
    headers: { accept: "application/json", "user-agent": "KORAX/1.0 (football app)" },
    redirect: "follow",
    signal: AbortSignal.timeout(12000),
  });
  if (!response.ok) {
    const error = new Error("SportScore returned HTTP " + response.status);
    error.status = response.status === 429 ? 503 : response.status;
    throw error;
  }
  return response.json();
}

async function cachedProviderRequest(path, params, env, ttlSeconds) {
  const url = new URL(path, SPORTSCORE);
  for (const [key, value] of Object.entries(params || {})) {
    if (value !== undefined && value !== null && value !== "") url.searchParams.set(key, String(value));
  }
  const cache = caches.default;
  const cacheKey = new Request(url.toString(), { method: "GET" });
  const cached = await cache.match(cacheKey);
  if (cached) return cached.json();
  const payload = await providerRequest(path, params);
  await cache.put(cacheKey, new Response(JSON.stringify(payload), {
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "public, max-age=" + ttlSeconds },
  }));
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


// Multi-publisher RSS aggregation. No API key and no full-article republication.
const NEWS_FEEDS = [
  { id: "btolat", source: "بطولات", language: "ar", url: "https://www.btolat.com/rss/newsfeed" },
  { id: "btolat-egypt", source: "بطولات - الدوري المصري", language: "ar", url: "https://www.btolat.com/rss/newsfeed?leagueid=1193" },
  { id: "btolat-spain", source: "بطولات - الدوري الإسباني", language: "ar", url: "https://www.btolat.com/rss/newsfeed?leagueid=1399" },
  { id: "btolat-england", source: "بطولات - الدوري الإنجليزي", language: "ar", url: "https://www.btolat.com/rss/newsfeed?leagueid=1204" },
  { id: "btolat-ahly", source: "بطولات - الأهلي", language: "ar", url: "https://www.btolat.com/rss/newsfeed?teamid=8883" },
  { id: "btolat-zamalek", source: "بطولات - الزمالك", language: "ar", url: "https://www.btolat.com/rss/newsfeed?teamid=8959" },
  { id: "btolat-barcelona", source: "بطولات - برشلونة", language: "ar", url: "https://www.btolat.com/rss/newsfeed?teamid=15702" },
  { id: "btolat-real-madrid", source: "بطولات - ريال مدريد", language: "ar", url: "https://www.btolat.com/rss/newsfeed?teamid=16110" },
  { id: "btolat-liverpool", source: "بطولات - ليفربول", language: "ar", url: "https://www.btolat.com/rss/newsfeed?teamid=9249" },
  { id: "btolat-man-city", source: "بطولات - مانشستر سيتي", language: "ar", url: "https://www.btolat.com/rss/newsfeed?teamid=9259" },
  { id: "btolat-man-utd", source: "بطولات - مانشستر يونايتد", language: "ar", url: "https://www.btolat.com/rss/newsfeed?teamid=9260" },
  { id: "btolat-arsenal", source: "بطولات - آرسنال", language: "ar", url: "https://www.btolat.com/rss/newsfeed?teamid=9002" },
  { id: "btolat-juventus", source: "بطولات - يوفنتوس", language: "ar", url: "https://www.btolat.com/rss/newsfeed?teamid=11922" },
  { id: "btolat-psg", source: "بطولات - باريس سان جيرمان", language: "ar", url: "https://www.btolat.com/rss/newsfeed?teamid=10061" },
  { id: "btolat-pyramids", source: "بطولات - بيراميدز", language: "ar", url: "https://www.btolat.com/rss/newsfeed?teamid=23165" },
  { id: "youm7", source: "الإخبارية – تغطية اليوم السابع", language: "ar", url: "https://www.youm7.com/rss/SectionRss?SectionID=298", fallbackUrl: "https://alikhbariya.net/feeds/sources/f903fc12-4e08-4331-b249-81551c16f840.xml" },
  { id: "hesport", source: "هسبورت", language: "ar", url: "https://www.hesport.com/feed" },
  { id: "kooora", source: "كووورة", language: "ar", url: "https://feeds.footballco.com/kooora/feed/6p5bsxot7te8yick" },
  { id: "aljazeera", source: "الجزيرة", language: "ar", url: "https://www.aljazeera.net/rss" },
  { id: "sky-arabia", source: "سكاي نيوز عربية", language: "ar", url: "https://www.skynewsarabia.com/rss" },
  { id: "bbc-football", source: "BBC Sport", language: "en", url: "https://feeds.bbci.co.uk/sport/football/rss.xml" },
  { id: "bbc-premier", source: "BBC Sport - Premier League", language: "en", url: "https://feeds.bbci.co.uk/sport/football/premier-league/rss.xml" },
  { id: "bbc-champions", source: "BBC Sport - Champions League", language: "en", url: "https://feeds.bbci.co.uk/sport/football/champions-league/rss.xml" },
  { id: "bbc-europe", source: "BBC Sport - European Football", language: "en", url: "https://feeds.bbci.co.uk/sport/football/european/rss.xml" },
  { id: "sky-football", source: "Sky Sports", language: "en", url: "https://www.skysports.com/rss/11095" },
  { id: "sky-premier", source: "Sky Sports - Premier League", language: "en", url: "https://www.skysports.com/rss/11661" },
  { id: "guardian-football", source: "The Guardian - Football", language: "en", url: "https://www.theguardian.com/football/rss" },
];

const FOOTBALL_WORDS = /football|soccer|premier league|champions league|la liga|serie a|bundesliga|ligue 1|saudi pro league|transfer|arsenal|chelsea|liverpool|manchester|real madrid|barcelona|bayern|psg|كرة القدم|كورة|الدوري|الأهلي|الزمالك|ريال مدريد|برشلونة|ليفربول|مانشستر|الهلال|النصر|الاتحاد|محمد صلاح|عمر مرموش|انتقال|ميركاتو|مدرب|مباراة|هدف|أهداف|لاعب|منتخب/i;

function decodeXml(value) {
  return String(value || "")
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
    .replace(/&#x([0-9a-f]+);/gi, (_, code) => {
      const point = parseInt(code, 16);
      return Number.isFinite(point) && point >= 0 && point <= 0x10ffff
        ? String.fromCodePoint(point) : "";
    })
    .replace(/&#([0-9]+);/g, (_, code) => {
      const point = Number(code);
      return Number.isFinite(point) && point >= 0 && point <= 0x10ffff
        ? String.fromCodePoint(point) : "";
    })
    .replace(/&nbsp;/gi, " ").replace(/&quot;/gi, '"').replace(/&apos;/gi, "'")
    .replace(/&lt;/gi, "<").replace(/&gt;/gi, ">").replace(/&amp;/gi, "&");
}

function decodeHtml(value) {
  return decodeXml(String(value || ""))
    .replace(/&#39;/gi, "'").replace(/&#34;/gi, '"');
}

function htmlText(value) {
  let text = String(value || "").replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1");
  // Publishers sometimes encode HTML twice (for example &amp;lt;p&amp;gt;).
  // Decode before stripping tags, and repeat once for double-encoded markup.
  for (let attempt = 0; attempt < 2; attempt += 1) {
    const decoded = decodeXml(text);
    if (decoded === text) break;
    text = decoded;
  }
  return text
    .replace(/<script\b[\s\S]*?<\/script>/gi, " ")
    .replace(/<style\b[\s\S]*?<\/style>/gi, " ")
    .replace(/<!--[\s\S]*?-->/g, " ")
    .replace(/<[^>]*>/g, " ")
    .replace(/\]\]>/g, " ")
    .replace(/\s+/gu, " ").trim();
}

function xmlField(block, name) {
  const re = new RegExp("<(?:[\\w.-]+:)?" + name + "\\b([^>]*)>([\\s\\S]*?)<\\/(?:[\\w.-]+:)?" + name + "\\s*>", "i");
  const match = String(block || "").match(re);
  return match ? { attrs: match[1] || "", value: match[2] || "" } : { attrs: "", value: "" };
}

function xmlAttribute(attributes, name) {
  const re = new RegExp("\\b" + name + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", "i");
  const match = String(attributes || "").match(re);
  return decodeHtml(match ? (match[1] ?? match[2] ?? match[3] ?? "") : "");
}

function safeNewsUrl(raw, base) {
  if (!raw) return "";
  try {
    const url = new URL(decodeHtml(raw).trim(), base);
    return url.protocol === "https:" ? url.toString() : "";
  } catch { return ""; }
}

function extractFeedImage(block, baseUrl) {
  const structuredTags = String(block || "").match(/<(?:[\w.-]+:)?(?:content|thumbnail|enclosure)\b[^>]*\/?\s*>/gi) || [];
  for (const tag of structuredTags) {
    const attributes = tag.slice(tag.indexOf(" ") + 1);
    const candidate = xmlAttribute(attributes, "url") || xmlAttribute(attributes, "href");
    if (!candidate) continue;
    const mime = xmlAttribute(attributes, "type").toLowerCase();
    const isImage = mime.startsWith("image/") ||
      /\.(?:png|jpe?g|webp|gif|avif)(?:[?#]|$)/i.test(candidate);
    if (!isImage) continue;
    const safe = safeNewsUrl(candidate, baseUrl);
    if (safe) return safe;
  }

  const imageTags = String(block || "").match(/<img\b[^>]*>/gi) || [];
  for (const tag of imageTags) {
    const attributes = tag.replace(/^<img\b/i, "").replace(/>$/, "");
    const candidate = xmlAttribute(attributes, "data-src") ||
      xmlAttribute(attributes, "data-original") ||
      xmlAttribute(attributes, "src") ||
      xmlAttribute(attributes, "srcset").split(",")[0].trim().split(/\s+/)[0];
    const safe = safeNewsUrl(candidate, baseUrl);
    if (safe) return safe;
  }

  const imageUrl = xmlField(block, "image").value;
  const nestedUrl = xmlField(imageUrl, "url").value;
  return safeNewsUrl(htmlText(nestedUrl), baseUrl);
}

function classifyNewsCategory(title, summary, feed = {}) {
  const text = (String(title || "") + " " + String(summary || "")).toLowerCase();

  if (/transfer|transfers|انتقال|ميركاتو|سوق الانتقالات|صفقة جديدة/i.test(text)) return "transfers";
  if (feed.id === "btolat-egypt") return "egypt";
  if (feed.id === "btolat-spain") return "la-liga";
  if (["btolat-egypt", "btolat-ahly", "btolat-zamalek", "btolat-pyramids"].includes(feed.id)) return "egypt";
  if (["btolat-spain", "btolat-barcelona", "btolat-real-madrid"].includes(feed.id)) return "la-liga";
  if (["btolat-england", "btolat-liverpool", "btolat-man-city", "btolat-man-utd", "btolat-arsenal", "bbc-premier", "sky-premier"].includes(feed.id)) return "premier-league";
  if (feed.id === "btolat-juventus") return "serie-a";
  if (feed.id === "btolat-psg") return "ligue-1";

  if (/la liga|الدوري الإسباني|ريال مدريد|برشلونة|فياريال|أتلتيكو مدريد|اتلتيكو مدريد|real madrid|barcelona|villarreal|atletico madrid/i.test(text)) {
    return "la-liga";
  }
  if (/premier league|الدوري الإنجليزي|ليفربول|مانشستر سيتي|مانشستر يونايتد|آرسنال|ارسنال|تشيلسي|tottenham|liverpool|manchester city|manchester united|arsenal|chelsea/i.test(text)) {
    return "premier-league";
  }
  if (/serie a|الدوري الإيطالي|إنتر ميلان|انتر ميلان|يوفنتوس|ميلان|نابولي|inter milan|juventus|ac milan|napoli/i.test(text)) {
    return "serie-a";
  }
  if (/bundesliga|الدوري الألماني|بايرن ميونخ|بوروسيا دورتموند|bayern munich|borussia dortmund/i.test(text)) {
    return "bundesliga";
  }
  if (/ligue 1|الدوري الفرنسي|باريس سان جيرمان|باريس سان|مارسيليا|موناكو|paris saint-germain|marseille/i.test(text)) {
    return "ligue-1";
  }
  if (/الدوري السعودي|السعودية|دوري روشن|saudi pro league|الهلال السعودي|النصر السعودي|الاتحاد السعودي|الأهلي السعودي|الاهلي السعودي|al.?hilal|al.?nassr|al.?ittihad|al.?ahli/i.test(text)) {
    return "saudi";
  }
  if (/الدوري المصري|كأس مصر|الأهلي المصري|الاهلي المصري|الزمالك|بيراميدز|منتخب مصر|egyptian premier league/i.test(text)) {
    return "egypt";
  }
  return "football";
}

function parseRssFeed(xml, feed) {
  const blocks = String(xml || "").match(/<(?:item|entry)\b[\s\S]*?<\/(?:item|entry)>/gi) || [];
  return blocks.slice(0, 60).map((block) => {
    const title = htmlText(xmlField(block, "title").value);
    const description = xmlField(block, "description").value ||
      xmlField(block, "summary").value || xmlField(block, "content").value ||
      xmlField(block, "encoded").value || "";
    const summary = htmlText(description).slice(0, 360);
    const link = xmlField(block, "link");
    const atomLink = block.match(/<link\b([^>]*)\/?\s*>/i);
    const rawLink = xmlAttribute(link.attrs || atomLink?.[1], "href") ||
      htmlText(link.value) || htmlText(xmlField(block, "guid").value);
    const url = safeNewsUrl(rawLink, feed.url);
    const rawDate = htmlText(xmlField(block, "pubDate").value) ||
      htmlText(xmlField(block, "published").value) ||
      htmlText(xmlField(block, "updated").value) ||
      htmlText(xmlField(block, "date").value);
    const timestamp = Date.parse(rawDate);
    const imageUrl = extractFeedImage(block, feed.url);
    if (!title || !url) return null;
    if (feed.id === "aljazeera" || feed.id === "sky-arabia" || feed.id === "hesport") {
      if (!FOOTBALL_WORDS.test(title + " " + summary)) return null;
    }
    return {
      id: url, title, summary, url, source: feed.source,
      publishedAt: Number.isFinite(timestamp) ? new Date(timestamp).toISOString() : rawDate,
      imageUrl: imageUrl || null, language: feed.language,
      category: classifyNewsCategory(title, summary, feed),
    };
  }).filter(Boolean);
}

async function fetchRssFeedUrl(url, feed) {
  const response = await fetch(url, {
    headers: {
      accept: "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8",
      "user-agent": "Mozilla/5.0 (compatible; KORAX-News/1.0; +https://github.com/shotalx2-lgtm/korax-)",
      "accept-language": "ar,en;q=0.8",
    },
    redirect: "follow",
    signal: AbortSignal.timeout(10000),
  });
  if (!response.ok) throw new Error(feed.id + " RSS HTTP " + response.status);
  return parseRssFeed(await response.text(), feed);
}

async function fetchRssFeed(feed) {
  try {
    const articles = await fetchRssFeedUrl(feed.url, feed);
    if (articles.length || !feed.fallbackUrl) return articles;
    throw new Error(feed.id + " RSS returned no parseable articles");
  } catch (primaryError) {
    if (!feed.fallbackUrl) throw primaryError;
    const fallbackArticles = await fetchRssFeedUrl(feed.fallbackUrl, { ...feed, url: feed.fallbackUrl });
    if (!fallbackArticles.length) throw new Error(feed.id + " primary and fallback RSS feeds returned no articles");
    return fallbackArticles;
  }
}

async function loadPublisherNews() {
  const responses = await Promise.allSettled(NEWS_FEEDS.map(fetchRssFeed));
  const articles = responses.flatMap((result) => result.status === "fulfilled" ? result.value : []);
  const unique = new Map();
  for (const article of articles) {
    const normalized = article.title.toLocaleLowerCase()
      .replace(/[^\p{L}\p{N}]+/gu, " ").trim();
    const key = article.url || normalized;
    if (normalized && !unique.has(key)) unique.set(key, article);
  }
  return {
    data: Array.from(unique.values()).sort((a, b) =>
      (Date.parse(b.publishedAt) || 0) - (Date.parse(a.publishedAt) || 0)).slice(0, 100),
    language: "ar+en",
    updatedAt: new Date().toISOString(),
    sources: NEWS_FEEDS.map((feed) => feed.source),
    feedsAvailable: responses.filter((result) => result.status === "fulfilled").length,
    feedsTotal: NEWS_FEEDS.length,
    feedErrors: responses.map((result, index) => result.status === "rejected" ? { feed: NEWS_FEEDS[index].id, error: String(result.reason?.message || result.reason) } : null).filter(Boolean),
  };
}

async function getCachedArabicNews() {
  const cache = caches.default;
  const cacheKey = new Request("https://korax-cache.invalid/api/news?version=multi-publisher-rss-4");
  const cached = await cache.match(cacheKey);
  if (cached) return cached.json();
  const payload = await loadPublisherNews();
  await cache.put(cacheKey, new Response(JSON.stringify(payload), {
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "public, max-age=300" },
  }));
  return payload;
}

const FANTASY_LEAGUE_TARGETS = [
  { key: "england", slug: "jednm9whz0ryox8", label: "الدوري الإنجليزي", search: "English Premier League" },
  { key: "spain", slug: "vl7oqdehlyr510j", label: "الدوري الإسباني", search: "Spanish La Liga" },
  { key: "france", slug: "yl5ergphnzr8k0o", label: "الدوري الفرنسي", search: "French Ligue 1" },
  { key: "italy", slug: "4zp5rzghp5q82w1", label: "الدوري الإيطالي", search: "Italian Serie A" },
  { key: "germany", slug: "gy0or5jhg6qwzv3", label: "الدوري الألماني", search: "Bundesliga" },
  { key: "saudi", slug: "", label: "الدوري السعودي", search: "Saudi Pro League" },
];

function fantasyPosition(value) {
  const name = nestedName(value).toLowerCase();
  if (name.includes("goal") || name.includes("keeper") || name === "gk") return "GK";
  if (name.includes("defen") || name === "def") return "DEF";
  if (name.includes("mid")) return "MID";
  if (name.includes("attack") || name.includes("forward") || name.includes("strik") || name === "fwd") return "FWD";
  return "";
}

function numericStat(details, ...names) {
  const wanted = names.map((name) => name.toLowerCase().replace(/[^a-z]/g, ""));
  const item = Array.isArray(details) ? details.find((entry) =>
    wanted.includes(nestedName(entry?.type).toLowerCase().replace(/[^a-z]/g, ""))) : null;
  const raw = item?.value ?? item?.total ?? 0;
  if (typeof raw === "number") return raw;
  if (typeof raw === "string") return Number(raw) || 0;
  if (raw && typeof raw === "object") return Number(raw.total ?? raw.all?.count ?? raw.count ?? raw.value) || 0;
  return 0;
}

function calculateSeasonFantasyPoints(position, details) {
  const appearances = numericStat(details, "Appearances", "Appearences");
  const goals = numericStat(details, "Goals");
  const assists = numericStat(details, "Assists");
  const cleanSheets = numericStat(details, "Clean Sheets", "Cleansheets");
  const saves = numericStat(details, "Saves");
  const penaltySaves = numericStat(details, "Penalty Saves", "Penalties Saved");
  const penaltyMisses = numericStat(details, "Penalties Missed", "Penalty Misses");
  const yellowCards = numericStat(details, "Yellow Cards");
  const redCards = numericStat(details, "Red Cards");
  const ownGoals = numericStat(details, "Own Goals");
  const goalsConceded = numericStat(details, "Goals Conceded");
  const goalValue = position === "GK" || position === "DEF" ? 6 : position === "MID" ? 5 : 4;
  const cleanSheetValue = position === "GK" || position === "DEF" ? 4 : position === "MID" ? 1 : 0;
  const concededPenalty = position === "GK" || position === "DEF" ? Math.floor(goalsConceded / 2) : 0;
  return Math.round(appearances * 2 + goals * goalValue + assists * 3 + cleanSheets * cleanSheetValue
    + Math.floor(saves / 3) + penaltySaves * 5 - penaltyMisses * 2 - yellowCards - redCards * 3 - ownGoals * 2 - concededPenalty);
}

function scoreValue(row, ...keys) {
  for (const key of keys) {
    const value = row?.[key] ?? row?.stats?.[key] ?? row?.statistics?.[key];
    if (value !== undefined && value !== null && value !== "") {
      const number = Number(typeof value === "object" ? value.total ?? value.value ?? value.count : value);
      if (Number.isFinite(number)) return number;
    }
  }
  return 0;
}

function normalizeFantasyPlayer(row, league) {
  const player = row?.player && typeof row.player === "object" ? row.player : row;
  const team = row?.team || row?.club || player?.team || player?.club || {};
  const name = nestedName(player) || row?.player_name || row?.name || "";
  const id = String(player?.slug || player?.id || row?.player_slug || row?.player_id || row?.slug || "");
  const position = fantasyPosition(player?.position || row?.position || player?.role || row?.role ||
    row?.player_position || row?.position_name || player?.position_name || row?.role_name || "");
  if (!id || !name || !position) return null;
  const goals = scoreValue(row, "goals", "goal", "total_goals");
  const assists = scoreValue(row, "assists", "total_assists");
  const appearances = scoreValue(row, "appearances", "apps", "matches");
  const details = [
    { type: { name: "Goals" }, value: goals }, { type: { name: "Assists" }, value: assists },
    { type: { name: "Appearances" }, value: appearances },
    { type: { name: "Clean Sheets" }, value: scoreValue(row, "clean_sheets", "cleanSheets") },
    { type: { name: "Saves" }, value: scoreValue(row, "saves") },
    { type: { name: "Yellow Cards" }, value: scoreValue(row, "yellow_cards", "yellowCards") },
    { type: { name: "Red Cards" }, value: scoreValue(row, "red_cards", "redCards") },
  ];
  const image = player?.image || player?.image_url || player?.photo || player?.photo_url ||
    row?.player_image || row?.player_image_url || row?.image || row?.image_url || "";
  const club = nestedName(team) || row?.team_name || row?.club_name || "";
  const price = position === "GK" ? 4.5 : position === "DEF" ? 5.0 : position === "MID" ? 6.0 : 6.5;
  return {
    id: "sportscore-" + id, providerId: id, name, club, teamId: String(team?.id || team?.slug || ""),
    position, price, points: calculateSeasonFantasyPoints(position, details),
    imageUrl: typeof image === "string" && image.startsWith("https://") ? image : null,
    league: league.label, leagueId: league.slug,
    stats: { appearances, goals, assists, cleanSheets: scoreValue(row, "clean_sheets", "cleanSheets"), saves: scoreValue(row, "saves") },
  };
}

async function resolveFantasyCompetition(target, env) {
  if (target.slug) return target.slug;
  const payload = await cachedProviderRequest("/api/v1/search/", {
    q: target.search, sport: "football", limit: 20,
  }, env, 21600);
  const root = unwrap(payload);
  const rows = Array.isArray(root?.competitions) ? root.competitions
    : Array.isArray(root?.leagues) ? root.leagues
    : Array.isArray(root?.tournaments) ? root.tournaments
    : Array.isArray(root?.results) ? root.results.filter((item) => String(item.type || "").toLowerCase().includes("competition")) : [];
  const matchName = (item) => (nestedName(item) || item?.title || item?.competition_name || item?.league_name || "").toLowerCase();
  const match = rows.find((item) => {
    const name = matchName(item);
    return name.includes("saudi") && (name.includes("pro") || name.includes("professional"));
  }) || rows.find((item) => matchName(item).includes("saudi"));
  const urlSlug = String(match?.url || match?.href || "").match(/\/([a-z0-9]{8,})\/?$/i)?.[1] || "";
  return match?.slug || match?.competition_slug || match?.url_slug || urlSlug;
}

async function loadFantasyLeaguePlayers(target, env) {
  const slug = await resolveFantasyCompetition(target, env);
  if (!slug) return {
    key: target.key, name: target.label, leagueId: "", available: false,
    reason: "competition_not_found", players: [],
  };
  const [goalsPayload, assistsPayload] = await Promise.all([
    cachedProviderRequest("/api/v1/topscorers/", { sport: "football", slug, limit: 50, stat: "goals" }, env, 3600),
    cachedProviderRequest("/api/v1/topscorers/", { sport: "football", slug, limit: 50, stat: "assists" }, env, 3600),
  ]);
  const rowsFor = (payload) => {
    const root = unwrap(payload);
    if (Array.isArray(root)) return root;
    for (const key of ["players", "topscorers", "top_scorers", "scorers", "assists", "data"]) {
      if (Array.isArray(root?.[key])) return root[key];
    }
    return [];
  };
  const unique = new Map();
  for (const payload of [goalsPayload, assistsPayload]) {
    for (const row of rowsFor(payload)) {
      const player = normalizeFantasyPlayer(row, { ...target, slug });
      if (player && !unique.has(player.id)) unique.set(player.id, player);
    }
  }
  const players = Array.from(unique.values());
  return {
    key: target.key, name: target.label, leagueId: slug, available: players.length > 0,
    reason: players.length ? "leaderboards_only" : "no_leaderboard_data", players,
  };
}

async function getCachedFantasyPlayers(env) {
  const cache = caches.default;
  const cacheKey = new Request("https://korax-cache.invalid/api/fantasy/players?version=sportscore-1");
  const cached = await cache.match(cacheKey);
  if (cached) return cached.json();
  const results = await Promise.allSettled(FANTASY_LEAGUE_TARGETS.map((target) => loadFantasyLeaguePlayers(target, env)));
  const leagues = results.map((result, index) => result.status === "fulfilled" ? result.value : ({
    key: FANTASY_LEAGUE_TARGETS[index].key, name: FANTASY_LEAGUE_TARGETS[index].label,
    leagueId: FANTASY_LEAGUE_TARGETS[index].slug, available: false, reason: "provider_unavailable", players: [],
  }));
  const unique = new Map();
  for (const league of leagues) for (const player of league.players || []) if (!unique.has(player.id)) unique.set(player.id, player);
  const payload = {
    data: Array.from(unique.values()),
    leagues: leagues.map(({ players, ...summary }) => ({ ...summary, playerCount: players.length })),
    meta: {
      playerCount: unique.size, scoringVersion: "KORAX-SEASON-1", updatedAt: new Date().toISOString(),
      catalogueScope: "top_scorers_and_assists_only", roundScoringAvailable: false,
      limitation: "SportScore does not expose a complete squad endpoint. This is a leaderboard-only player catalogue, not a full 15-player market or live gameweek scoring source.",
    },
    source: "sportscore",
  };
  await cache.put(cacheKey, new Response(JSON.stringify(payload), {
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "public, max-age=3600" },
  }));
  return payload;
}

const KORAX_COMPETITIONS = [
  { id: "jednm9whz0ryox8", slug: "jednm9whz0ryox8", name: "الدوري الإنجليزي", country: "إنجلترا" },
  { id: "vl7oqdehlyr510j", slug: "vl7oqdehlyr510j", name: "الدوري الإسباني", country: "إسبانيا" },
  { id: "yl5ergphnzr8k0o", slug: "yl5ergphnzr8k0o", name: "الدوري الفرنسي", country: "فرنسا" },
  { id: "4zp5rzghp5q82w1", slug: "4zp5rzghp5q82w1", name: "الدوري الإيطالي", country: "إيطاليا" },
  { id: "gy0or5jhg6qwzv3", slug: "gy0or5jhg6qwzv3", name: "الدوري الألماني", country: "ألمانيا" },
  { id: "saudi-pro-league", slug: "saudi-pro-league", name: "الدوري السعودي", country: "السعودية" },
  { id: "z8yomo4h7wq0j6l", slug: "z8yomo4h7wq0j6l", name: "دوري أبطال أوروبا", country: "أوروبا" },
];


async function route(request, env) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "") || "/";
  const perPage = Math.min(50, Math.max(1, Number(url.searchParams.get("per_page") || 30)));

  if (path === "/health") return json({
    ok: true, service: "KORAX API", provider: "SportScore", providerConfigured: true,
    providerStatus: "not_checked", apiKeyRequired: false, newsProvider: "publisher-rss",
  });

  if (path === "/api/news") {
    const payload = await getCachedArabicNews();
    const requested = Math.min(100, Math.max(1, Number(url.searchParams.get("per_page") || 40)));
    const language = url.searchParams.get("language");
    const category = url.searchParams.get("category");
    let data = payload.data;
    if (language === "ar" || language === "en") data = data.filter((item) => item.language === language);
    if (category) data = data.filter((item) => item.category === category);
    return json({ ...payload, data: data.slice(0, requested), source: "publisher-rss" });
  }

  if (path === "/api/fantasy/players") return json(await getCachedFantasyPlayers(env));

  if (path === "/api/matches") {
    const live = url.searchParams.get("live") === "1";
    const date = url.searchParams.get("date") || new Date().toISOString().slice(0, 10);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return json({ error: "date must use YYYY-MM-DD" }, 400);
    // SportScore's widget match summaries include the stable match slug used by
    // its detail endpoint. The dated fixtures endpoint remains the schedule source.
    const providerPath = live ? "/api/widget/matches/" : "/api/v1/fixtures/";
    const params = live ? { sport: "football", limit: 50 } : { sport: "football", date, limit: 100 };
    const payload = await cachedProviderRequest(providerPath, params, env, live ? 20 : 120);
    const raw = unwrap(payload);
    const rows = Array.isArray(raw) ? raw : (raw?.fixtures || raw?.matches || raw?.results || raw?.data || []);
    const matches = live ? rows.filter(isLiveFixture) : rows;
    return json({
      data: matches.map(normalizeFixture),
      meta: { count: matches.length, date, live },
      source: "sportscore", updatedAt: new Date().toISOString(),
    });
  }

  const match = path.match(/^\/api\/matches\/([A-Za-z0-9_-]+)$/);
  if (match) {
    const payload = await cachedProviderRequest("/api/widget/match/", { sport: "football", slug: match[1] }, env, 30);
    const root = unwrap(payload);
    const item = root?.match || root?.data || root;
    return item ? json({ data: normalizeFixture(item), source: "sportscore" }) : json({ error: "Match not found" }, 404);
  }

  if (path === "/api/competitions") {
    const search = (url.searchParams.get("search") || "").trim();
    if (search.length >= 2) {
      const payload = await cachedProviderRequest("/api/v1/search/", {
        q: search, sport: "football", limit: perPage,
      }, env, 300);
      const root = unwrap(payload);
      const keys = ["competitions", "leagues", "tournaments", "cups"];
      let rows = [];
      for (const key of keys) {
        if (Array.isArray(root?.[key])) rows.push(...root[key]);
      }
      if (!rows.length && Array.isArray(root?.results)) {
        rows = root.results.filter((row) => {
          const kind = String(row?.type || row?.entity_type || row?.category || "").toLowerCase();
          return /competition|league|tournament|cup|championship/.test(kind);
        });
      }
      const data = rows.map((item) => {
        const rawUrl = String(item?.url || item?.href || item?.link || "");
        const urlSlug = rawUrl.match(/\/([a-z0-9_-]{5,})\/?(?:\?.*)?$/i)?.[1] || "";
        const slug = String(item?.slug || item?.competition_slug || item?.league_slug ||
          item?.url_slug || urlSlug || item?.id || "");
        const kind = String(item?.type || item?.entity_type || item?.category || "").toLowerCase();
        const image = item?.image_path || item?.image_url || item?.logo || item?.logo_url || item?.image || "";
        return {
          id: String(item?.id || slug),
          slug,
          name: nestedName(item) || item?.title || item?.common_name || item?.name || "",
          country: nestedName(item?.country) || item?.country_name || "",
          imageUrl: typeof image === "string" && /^https:\/\//i.test(image) ? image : "",
          kind: /tournament|cup|championship/.test(kind) ? "tournament" : "league",
          season: item?.season?.name || item?.season || "",
        };
      }).filter((item) => item.slug && item.name);
      return json({ data, source: "sportscore-search", meta: { count: data.length, query: search } });
    }
    return json({
      data: KORAX_COMPETITIONS.map((item) => ({
        ...item,
        kind: item.slug === "z8yomo4h7wq0j6l" ? "tournament" : "league",
        imageUrl: "", logoUrl: "",
      })),
      source: "sportscore-competition-index",
    });
  }

  const competition = path.match(/^\/api\/competitions\/([A-Za-z0-9_-]+)$/);
  if (competition) {
    let slug = competition[1];
    if (slug === "saudi-pro-league") {
      const target = FANTASY_LEAGUE_TARGETS.find((item) => item.key === "saudi");
      slug = await resolveFantasyCompetition(target, env);
    }
    if (!slug) return json({ error: "Competition not found" }, 404);
    const payload = await cachedProviderRequest("/api/widget/standings/", { sport: "football", slug }, env, 600);
    return json({ data: unwrap(payload), source: "sportscore" });
  }

  if (path === "/api/teams" || path === "/api/players") {
    const search = (url.searchParams.get("search") || "").trim();
    if (search.length < 2) return json({ data: [], meta: { requiresSearch: true }, source: "sportscore-search" });
    const payload = await cachedProviderRequest("/api/v1/search/", { q: search, sport: "football", limit: perPage }, env, 300);
    const root = unwrap(payload);
    const kind = path === "/api/teams" ? "team" : "player";
    const keys = kind === "team" ? ["teams", "team"] : ["players", "player"];
    let rows = [];
    for (const key of keys) if (Array.isArray(root?.[key])) { rows = root[key]; break; }
    if (!rows.length && Array.isArray(root?.results)) rows = root.results.filter((row) => String(row.type || "").toLowerCase().includes(kind));
    if (kind === "team") {
      rows = rows.map((row) => {
        const descriptor = [
          row?.type, row?.entity_type, row?.team_type, row?.category,
          row?.gender, row?.name, row?.common_name,
        ].map((value) => nestedName(value) || String(value || "")).join(" ").toLowerCase();
        const explicitlyNational = row?.national === true || row?.is_national === true ||
          row?.national_team === true || row?.type === "national_team" ||
          row?.entity_type === "national_team" || /national team|men national|women national|منتخب/.test(descriptor);
        return { ...row, koraxKind: explicitlyNational ? "national" : "club" };
      });
    }
    return json({ data: rows, source: "sportscore" });
  }

  const team = path.match(/^\/api\/teams\/([A-Za-z0-9_-]+)$/);
  if (team) {
    const payload = await cachedProviderRequest("/api/widget/team/", { sport: "football", slug: team[1], limit: 30 }, env, 300);
    return json({ data: unwrap(payload), source: "sportscore" });
  }

  const player = path.match(/^\/api\/players\/([A-Za-z0-9_-]+)$/);
  if (player) {
    const payload = await cachedProviderRequest("/api/widget/player/", { sport: "football", slug: player[1] }, env, 300);
    return json({ data: unwrap(payload), source: "sportscore" });
  }

  if (path === "/api/transfers") {
    return json({
      data: [], source: "sportscore",
      meta: { available: false, reason: "provider_has_no_confirmed_transfers_endpoint" },
    });
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

export { fantasyPosition, numericStat, calculateSeasonFantasyPoints, htmlText, classifyNewsCategory, parseRssFeed, normalizeEvent, normalizeFixture, isLiveFixture };
