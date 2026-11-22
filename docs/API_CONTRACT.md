# KOORA backend API contract

The Android client intentionally contains no sports-provider secret. Set KOORA_API_BASE_URL to the public HTTPS origin of a backend you control. That backend must own provider keys, enforce rate limits, cache data and return licensed data only.

## News endpoint

- GET /api/news?per_page=40

Returns Arabic football articles with `id`, `title`, `summary`, `url`, `source`, `publishedAt`, and optional `imageUrl`. It aggregates RSS sources and caches responses at the edge. Images are publisher-supplied and may be unavailable for some articles.

## Fantasy player catalogue

- GET /api/fantasy/players

Returns the current-season player catalogue for the English Premier League, Spanish La Liga, French Ligue 1, Italian Serie A, German Bundesliga, and Saudi Pro League. Each player may include a stable provider ID, name, club, position, player image, KORAX starting price, season statistics, and KORAX season-total points. The response includes per-league availability so a competition missing from the provider subscription is not silently represented with sample players.

The endpoint resolves each league's current season, loads its participating teams, and then loads each team's season squad. Provider responses are cached for six hours. A cold catalogue refresh therefore uses multiple provider requests; it is not one request per end user when the cache is warm.

The initial point model is KORAX-specific and documented in `docs/FANTASY_SCORING.md`. It is a season aggregate, not matchweek scoring. Do not use it to claim live gameweek points or locked round results.

## List endpoints

All endpoints return either a JSON array or an object containing a data array.

- GET /api/matches
- GET /api/teams
- GET /api/players
- GET /api/competitions

## Detail endpoints

- GET /api/matches/{id}
- GET /api/teams/{id}
- GET /api/players/{id}
- GET /api/competitions/{id}

## Record fields

Common fields:
- id (required stable string or number)
- name or title
- subtitle (optional)
- imageUrl or logoUrl (optional HTTPS URL to official/licensed image)

Match fields:
- homeTeam and awayTeam (string or object with name and crest/logoUrl)
- homeScore and awayScore (only when confirmed)
- status
- startTime or utcDate
- competition (string or object with name)

Player fields may include position and team. Team fields may include country and stadium. Competition fields may include country.

The app never fabricates missing fields. If a score, image, lineup or event is unavailable, the UI says it is unavailable instead of using sample data.

## Example shapes

A match may look like:
{
  "id": "provider-match-id",
  "homeTeam": { "name": "Club A", "crest": "https://licensed.example/club-a.png" },
  "awayTeam": { "name": "Club B", "crest": "https://licensed.example/club-b.png" },
  "homeScore": 1,
  "awayScore": 0,
  "status": "LIVE",
  "startTime": "2026-11-19T18:00:00Z",
  "competition": { "name": "Competition" }
}

The example is a schema illustration only, not real match data.
