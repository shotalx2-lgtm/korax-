# KOORA API Worker

This Cloudflare Worker keeps the Sportmonks token on the server and normalizes provider data for the Android app.

## Configure
1. Create a Cloudflare Worker named koora-api.
2. Deploy this directory.
3. Add the provider token as a Worker secret (never as a normal variable):
   `npx wrangler secret put SPORTMONKS_API_TOKEN`
4. Set `ALLOWED_ORIGINS` to the app/web origins if browser clients are also used.
5. Set the Android Gradle property `KOORA_API_BASE_URL` to the Worker's HTTPS base URL.

## Routes
- `GET /health`
- `GET /api/matches?date=YYYY-MM-DD`
- `GET /api/matches?live=1`
- `GET /api/matches/{id}`
- `GET /api/teams`, `GET /api/teams/{id}`
- `GET /api/players`, `GET /api/players?search=name`, `GET /api/players/{id}`
- `GET /api/competitions`, `GET /api/competitions/{id}`

The adapter uses short edge caches for live fixtures and longer caches for static profiles. Provider plan limits, selected league coverage, image intellectual-property permissions and commercial redistribution terms still apply. Do not publish club logos or player photos until image rights have been cleared separately.
