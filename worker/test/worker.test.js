import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";
import { htmlText, classifyNewsCategory, parseRssFeed, normalizeFixture, isLiveFixture } from "../src/index.js";

function installCache() {
  const original = globalThis.caches;
  globalThis.caches = { default: { match: async () => undefined, put: async () => undefined } };
  return () => {
    if (original === undefined) delete globalThis.caches;
    else globalThis.caches = original;
  };
}

test("health endpoint confirms both data sources need no API key", async () => {
  const response = await worker.fetch(new Request("https://korax.test/health"), {});
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.ok, true);
  assert.equal(body.provider, "SportScore");
  assert.equal(body.apiKeyRequired, false);
  assert.equal(body.newsProvider, "publisher-rss");
  assert.equal(body.providerStatus, "not_checked");
});

test("SportScore live match summaries use stable slugs and normalize into the KORAX contract", async () => {
  const originalFetch = globalThis.fetch;
  const restoreCache = installCache();
  let requestedUrl = "";
  globalThis.fetch = async (input, init) => {
    requestedUrl = String(input);
    assert.equal(new Headers(init.headers).get("x-api-key"), null);
    assert.equal(new Headers(init.headers).get("authorization"), null);
    return new Response(JSON.stringify({
      matches: [{
        id: 987, slug: "al-ahly-vs-zamalek",
        home: "Al Ahly", away: "Zamalek",
        home_logo: "https://images.example/home.png",
        away_logo: "https://images.example/away.png",
        home_score: 2, away_score: 1,
        status: "live", status_text: "مباشر",
        time: "2026-11-22T18:00:00Z",
        competition: "Egyptian Premier League",
      }],
    }), { status: 200, headers: { "content-type": "application/json" } });
  };
  try {
    const response = await worker.fetch(
      new Request("https://korax.test/api/matches?live=1", { headers: { Origin: "https://app.example" } }),
      { ALLOWED_ORIGINS: "*" }
    );
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("access-control-allow-origin"), "*");
    assert.match(requestedUrl, /sportscore\.com\/api\/widget\/matches\//);
    assert.match(requestedUrl, /limit=50/);
    const body = await response.json();
    assert.equal(body.source, "sportscore");
    assert.equal(body.data[0].home, "Al Ahly");
    assert.equal(body.data[0].away, "Zamalek");
    assert.equal(body.data[0].homeScore, 2);
    assert.equal(body.data[0].awayScore, 1);
    assert.equal(body.data[0].homeTeam.crest, "https://images.example/home.png");
    assert.equal(body.data[0].id, "al-ahly-vs-zamalek");
  } finally {
    globalThis.fetch = originalFetch;
    restoreCache();
  }
});

test("RSS news aggregates Arabic and international feeds with publisher images", async () => {
  const originalFetch = globalThis.fetch;
  const restoreCache = installCache();
  const calls = [];
  globalThis.fetch = async (input) => {
    const url = String(input);
    calls.push(url);
    const arabic = /btolat|youm7|hesport|aljazeera|skynewsarabia/.test(url);
    const sourceTitle = arabic ? "خبر جديد عن كرة القدم والدوري" : "Latest football transfer and league news";
    const xml = "<?xml version=\"1.0\"?><rss><channel><item>" +
      "<title>" + sourceTitle + "</title>" +
      "<link>https://publisher.example/" + calls.length + "</link>" +
      "<description>Short football news summary for KORAX testing.</description>" +
      "<pubDate>Sun, 22 Nov 2026 17:00:00 GMT</pubDate>" +
      "<enclosure url=\"https://images.example/" + calls.length + ".jpg\" type=\"image/jpeg\"/>" +
      "</item></channel></rss>";
    return new Response(xml, { status: 200, headers: { "content-type": "application/rss+xml" } });
  };
  try {
    const response = await worker.fetch(new Request("https://korax.test/api/news?per_page=100"), {});
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.source, "publisher-rss");
    assert.equal(body.language, "ar+en");
    assert.equal(body.feedsTotal, 26);
    assert.ok(calls.includes("https://www.youm7.com/rss/SectionRss?SectionID=298"));
    assert.equal(body.feedsAvailable, 26, JSON.stringify(body.feedErrors));
    assert.equal(body.data.length, 26);
    assert.ok(body.data.some(item => item.language === "ar"));
    assert.ok(body.data.some(item => item.language === "en"));
    assert.ok(body.data.every(item => item.imageUrl?.startsWith("https://images.example/")));
    assert.equal(calls.length, 26);
    assert.ok(calls.every(url => !url.includes("news.google.com")));
  } finally {
    globalThis.fetch = originalFetch;
    restoreCache();
  }
});


test("Youm7 redirect failures fall back to a publisher-attributed RSS mirror", async () => {
  const originalFetch = globalThis.fetch;
  const restoreCache = installCache();
  const calls = [];
  globalThis.fetch = async (input) => {
    const url = String(input);
    calls.push(url);
    if (url.includes("www.youm7.com/rss/SectionRss")) {
      return new Response("redirect loop", { status: 508 });
    }
    const xml = `<rss><channel><item>
      <title><![CDATA[أخبار كرة القدم من اليوم السابع]]></title>
      <link>https://publisher.example/youm7-story</link>
      <description><![CDATA[ملخص الخبر الرياضي]]></description>
      <pubDate>Sun, 22 Nov 2026 17:00:00 GMT</pubDate>
      <enclosure url="https://images.example/youm7.jpg" type="image/jpeg"/>
    </item></channel></rss>`;
    return new Response(xml, { status: 200, headers: { "content-type": "application/rss+xml" } });
  };
  try {
    const response = await worker.fetch(new Request("https://korax.test/api/news?per_page=100"), {});
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.feedsAvailable, 26);
    assert.equal(body.feedErrors.some(item => item.feed === "youm7"), false);
    assert.ok(calls.includes("https://www.youm7.com/rss/SectionRss?SectionID=298"));
    assert.ok(calls.includes("https://alikhbariya.net/feeds/sources/f903fc12-4e08-4331-b249-81551c16f840.xml"));
    assert.ok(body.data.some(item => item.source === "الإخبارية – تغطية اليوم السابع"));
  } finally {
    globalThis.fetch = originalFetch;
    restoreCache();
  }
});

test("RSS text cleanup decodes encoded markup before stripping HTML", () => {
  assert.equal(
    htmlText("<![CDATA[&amp;lt;p&amp;gt;خبر مهم عن ريال مدريد&amp;lt;/p&amp;gt;]]>"),
    "خبر مهم عن ريال مدريد"
  );
});

test("news categorization avoids false Saudi labels from generic Arabic words", () => {
  assert.equal(
    classifyNewsCategory(
      "نتائج القسم الثاني لكرة القدم بالمغرب",
      "فاز الاتحاد الإسلامي الوجدي على منافسه في البطولة المغربية",
      { id: "hesport" }
    ),
    "football"
  );
  assert.equal(
    classifyNewsCategory("الزمالك يحقق الفوز في الدوري المصري", "", { id: "btolat" }),
    "egypt"
  );
  assert.equal(
    classifyNewsCategory("تطور جديد في صفقة انتقال لاعب", "", { id: "btolat" }),
    "transfers"
  );
});

test("news categorization recognizes the remaining European leagues and source-specific club feeds", () => {
  assert.equal(classifyNewsCategory("يوفنتوس يحقق الفوز", "", { id: "btolat-juventus" }), "serie-a");
  assert.equal(classifyNewsCategory("بايرن ميونخ يفوز في البوندسليغا", "", { id: "bbc-football" }), "bundesliga");
  assert.equal(classifyNewsCategory("باريس سان جيرمان يتأهل", "", { id: "btolat-psg" }), "ligue-1");
  assert.equal(classifyNewsCategory("الزمالك يعلن قرارًا جديدًا", "", { id: "btolat-zamalek" }), "egypt");
});

test("RSS parser reads media thumbnails and sanitizes publisher summaries", () => {
  const xml = `<?xml version="1.0"?>
    <rss><channel><item>
      <title><![CDATA[خبر عن برشلونة]]></title>
      <link>https://publisher.example/article</link>
      <description><![CDATA[&amp;lt;p&amp;gt;ملخص الخبر &amp;lt;b&amp;gt;بالتفصيل&amp;lt;/b&amp;gt;&amp;lt;/p&amp;gt;]]></description>
      <media:thumbnail url="https://images.example/news.jpg" />
      <pubDate>Sun, 22 Nov 2026 17:00:00 GMT</pubDate>
    </item></channel></rss>`;
  const [article] = parseRssFeed(xml, {
    id: "hesport", source: "هسبورت", language: "ar",
    url: "https://www.hesport.com/feed"
  });
  assert.equal(article.summary, "ملخص الخبر بالتفصيل");
  assert.equal(article.imageUrl, "https://images.example/news.jpg");
  assert.equal(article.category, "la-liga");
});

test("live fixture normalization retains the provider slug and nested scores", () => {
  const fixture = {
    fixture: {
      id: 987,
      slug: "club-a-vs-club-b",
      home: { id: 11, name: "Club A", image_path: "https://images.example/a.png" },
      away: { id: 22, name: "Club B", image_path: "https://images.example/b.png" },
      status: { name: "1st Half" },
      starting_at: "2026-11-23T00:00:00Z",
      competition: { name: "Example League" },
      scores: [
        { participant_id: 11, description: "CURRENT", score: { goals: 2 } },
        { participant_id: 22, description: "CURRENT", score: { goals: 1 } }
      ]
    }
  };
  const match = normalizeFixture(fixture);
  assert.equal(match.id, "club-a-vs-club-b");
  assert.equal(match.homeTeam.id, "11");
  assert.equal(match.awayTeam.id, "22");
  assert.equal(match.homeScore, 2);
  assert.equal(match.awayScore, 1);
  assert.equal(match.competitionName, "Example League");
  assert.equal(isLiveFixture(fixture), true);
  assert.equal(isLiveFixture({ status: "finished" }), false);
});

test("fantasy uses documented SportScore league leaderboards and exposes their limited scope", async () => {
  const originalFetch = globalThis.fetch;
  const restoreCache = installCache();
  const requested = [];
  globalThis.fetch = async (input, init) => {
    const url = new URL(String(input));
    requested.push(url);
    assert.equal(new Headers(init.headers).get("x-api-key"), null);
    assert.equal(new Headers(init.headers).get("authorization"), null);
    if (url.pathname === "/api/v1/search/") {
      return new Response(JSON.stringify({
        competitions: [{ name: "Saudi Pro League", slug: "saudi-pro-2026" }],
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
    const slug = url.searchParams.get("slug") || "unknown";
    return new Response(JSON.stringify({
      players: [{
        player: {
          id: "player-" + slug, slug: "player-" + slug,
          name: "Test Striker", position: "Forward",
          image: "https://images.example/player.png",
        },
        team: { name: "Test Club", slug: "test-club" },
        goals: 10, assists: 3, appearances: 12,
      }],
    }), { status: 200, headers: { "content-type": "application/json" } });
  };
  try {
    const response = await worker.fetch(new Request("https://korax.test/api/fantasy/players"), {});
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.source, "sportscore");
    assert.equal(body.leagues.length, 6);
    assert.equal(body.data.length, 6);
    assert.equal(body.meta.catalogueScope, "top_scorers_and_assists_only");
    assert.equal(body.meta.roundScoringAvailable, false);
    assert.ok(body.data.every(player => player.position === "FWD"));
    assert.ok(requested.some(url => url.pathname === "/api/v1/search/"));
    assert.ok(requested.filter(url => url.pathname === "/api/v1/topscorers/").length === 12);
    assert.ok(requested.some(url => url.searchParams.get("slug") === "jednm9whz0ryox8"));
    assert.ok(requested.some(url => url.searchParams.get("slug") === "saudi-pro-2026"));
  } finally {
    globalThis.fetch = originalFetch;
    restoreCache();
  }
});

test("SportScore has no confirmed transfer endpoint, so the API does not invent transfer records", async () => {
  const response = await worker.fetch(new Request("https://korax.test/api/transfers?per_page=50"), {});
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.deepEqual(body.data, []);
  assert.equal(body.meta.available, false);
});
