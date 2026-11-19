# Provider integration notes

KOORA's Android client talks only to the KOORA backend. It must never hold third-party provider tokens.

The initial adapter targets Sportmonks Football API v3. It converts provider responses into the stable app contract:
- Fixtures and livescores
- Match events, lineups, scores and statistics
- Team, player and league records

The chosen subscription must cover the leagues and endpoints enabled in production. Sportmonks tiers are selected by league count; the provider currently lists Starter (5 leagues), Growth (30), Pro (120) and Enterprise (all supported leagues) options. Verify exact league-level coverage before purchasing or advertising global coverage.

Photo and logo rights are not included automatically just because an API returns an image URL. Obtain written clearance or use a separately licensed image source.
