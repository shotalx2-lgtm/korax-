# KORAX Football News and Data Worker

Cloudflare Worker adapter for the KORAX Android app. It does not require third-party API keys for the selected public data sources.

## Providers

### Football data: SportScore

- Public REST API: https://sportscore.com/developers/api/
- No API key is required for the public attribution tier.
- Covers live/recent matches, date fixtures, match detail, team schedules, standings, top scorers/assists, and player search/profile data where available.
- Public free allowance is approximately 10,000 requests per 24 hours per source IP, subject to SportScore fair-use terms and change.
- Responses are cached at the edge to avoid one upstream request per app user.
- Required attribution is displayed in the Android matches screen as a clickable **Powered by SportScore** link to https://sportscore.com/.

Important coverage limits: SportScore does not document a full squad/player-catalogue endpoint or a confirmed transfers endpoint. KORAX therefore exposes a fantasy catalogue built from available league top-scorer/assist leaderboards only; this is not a complete player market and does not provide live gameweek scoring. The transfers endpoint returns an explicit unavailable state rather than inventing transfer records. Verify that the required competition slugs are supported by the live provider before enabling a league.

### News: publisher RSS feeds

The `GET /api/news` route aggregates public RSS feeds without a news API key:

- بطولات
- اليوم السابع (sports section)
- هسبورت
- الجزيرة (football-filtered items)
- سكاي نيوز عربية (football-filtered items)
- BBC Sport Football
- Sky Sports Football
- The Guardian Football

The response includes title, short summary, original article URL, publisher, publication date, language, category, and an image URL only when the feed supplies one. News is cached for five minutes. Feed availability and images depend on the publisher; no article body or image is copied from the article page. Respect publisher terms and image rights before commercial redistribution.

## Routes

- `GET /health`
- `GET /api/news?per_page=40` (optional `language=ar|en`, `category=...`)
- `GET /api/matches?date=YYYY-MM-DD`
- `GET /api/matches?live=1`
- `GET /api/matches/{slug}`
- `GET /api/competitions`
- `GET /api/competitions/{slug}` (standings)
- `GET /api/teams?search=name`, `GET /api/teams/{slug}`
- `GET /api/players?search=name`, `GET /api/players/{slug}`
- `GET /api/fantasy/players` (leaderboard-only catalogue)
- `GET /api/transfers` (explicitly unavailable with current provider)

## Deploy

1. Deploy the `worker/` directory to Cloudflare Workers.
2. Set the Android build property `KOORA_API_BASE_URL` to the Worker HTTPS origin. This is the backend URL, not a secret or API key.
3. No SportScore key or news API key is required for the documented public tiers.
4. Add a custom `ALLOWED_ORIGINS` value if you need to restrict browser origins.

Never put provider credentials or signing keys in the Android APK. For higher-volume or commercial use, confirm SportScore licensing and fair-use requirements directly with its maintainers.
