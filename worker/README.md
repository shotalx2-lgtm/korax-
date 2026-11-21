# KORAX Football News and Data Worker

This Cloudflare Worker has two independent jobs:

1. **News collection** — fetches public pages from the selected football publishers and returns their headlines, short excerpts, publication details and available article images.
2. **Football data** — optionally calls Sportmonks for match, player, team, competition and transfer records.

## News sources (no news API key)

The `GET /api/news` route reads directly from these publisher pages:

- 365Scores Arabic news
- GOAL Arabic news
- YallaKora news listing
- Kooora news listing
- MercatoDay homepage, with its public RSS feed as a fallback if the page is unavailable

News collection does **not** read `SPORTMONKS_API_TOKEN`, does not use Google News, and does not require a paid news provider or news API key. It collects headline-level content and short excerpts, keeps the source name and original article URL, and caches results at the edge for 180 seconds. Article images are used when supplied by the publisher page/feed. Access, page structure, availability and image rights remain controlled by each publisher; a blocked or changed publisher page can temporarily reduce results from that source.

The Android app still needs the HTTPS base URL of this Worker in `KOORA_API_BASE_URL` so it knows where to request the aggregated news. This is an endpoint address, not an API key.

## Deploy

1. Create or open the Cloudflare Worker named `koora-api`.
2. Deploy the `worker/` directory.
3. Set the Android build property `KOORA_API_BASE_URL` to the Worker's HTTPS base URL.
4. For live sports-data routes only, optionally add the Sportmonks token as a Worker secret:
   `npx wrangler secret put SPORTMONKS_API_TOKEN`

The news route remains available without that secret. Sportmonks credentials must never be put in the Android application.

## Routes

- `GET /health`
- `GET /api/news?per_page=40`
- `GET /api/transfers?per_page=40&page=1`
- `GET /api/matches?date=YYYY-MM-DD`
- `GET /api/matches?live=1`
- `GET /api/matches/{id}`
- `GET /api/teams`, `GET /api/teams/{id}`
- `GET /api/players`, `GET /api/players?search=name`, `GET /api/players/{id}`
- `GET /api/competitions`, `GET /api/competitions/{id}`

## Notes

- News and football-data routes are independent: news failures do not change fantasy scoring or match results.
- Sportmonks access is required only for the sports-data routes that call that provider. Coverage depends on the subscribed plan.
- Preserve source attribution and article links. Do not republish full protected articles.
- Confirm image and logo usage rights before commercial redistribution.
