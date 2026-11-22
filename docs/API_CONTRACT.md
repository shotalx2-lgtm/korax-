# KORAX backend API contract

The Android client talks only to the KORAX HTTPS backend. The current public-source integration requires no third-party API keys. Set `KOORA_API_BASE_URL` to the deployed Worker origin; this is an endpoint address, not an API key.

## News

- `GET /api/news?per_page=40`
- Optional filters: `language=ar|en`, `category=transfers|premier-league|la-liga|saudi|egypt|football`

Returns a mixed Arabic/English football-news feed with `id`, `title`, `summary`, `url`, `source`, `publishedAt`, `language`, `category`, and optional `imageUrl`. It aggregates publisher RSS feeds and caches at the edge for five minutes. Images are only included when supplied by the publisher feed; image availability and reuse rights are not guaranteed.

## Football data

SportScore public REST endpoints are used without an API key on the attribution tier. See https://sportscore.com/developers/api/ for current endpoint schemas and terms.

- `GET /api/matches?date=YYYY-MM-DD`
- `GET /api/matches?live=1`
- `GET /api/matches/{slug}`
- `GET /api/competitions`
- `GET /api/competitions/{slug}` (standings)
- `GET /api/teams?search=name`, `GET /api/teams/{slug}`
- `GET /api/players?search=name`, `GET /api/players/{slug}`

The Android matches screen displays the required clickable “Powered by SportScore” attribution. The documented anonymous tier is approximately 10,000 requests per 24 hours per IP, subject to provider fair-use terms; KORAX caches upstream responses to reduce usage.

## Fantasy limitations

- `GET /api/fantasy/players` collects the players returned by the league top-scorer and assist leaderboards for the English, Spanish, French, Italian, German and Saudi leagues.
- SportScore does not document a full squad catalogue endpoint. This is therefore not a complete player market; some players or leagues may be missing.
- The current returned points are a limited season aggregate derived from leaderboard statistics, not matchweek points.
- No confirmed transfers endpoint is documented by SportScore. `GET /api/transfers` returns an empty data list with `meta.available=false`, rather than fabricated transfers.

A complete 15-player market, locked gameweeks, live match-event scoring, automatic substitutions and official rankings require a provider/source with full squad and event-stat coverage plus a backend round engine. Do not advertise these as live features until implemented and verified.

## Response integrity

The app does not fabricate match scores, player positions, images, or transfer details. Missing fields remain empty or unavailable. All article items retain the publisher name and original link. Do not republish full protected articles or assume that a feed image is licensed for redistribution.
