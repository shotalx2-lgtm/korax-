# KORAX World Data Engine — Source Audit

Last reviewed: 2026-11-24

## Design rules

- SportScore remains the primary live-match source.
- All external requests run on scheduled server workflows; the Android app never calls providers directly.
- Every stored record retains its provider key and external ID.
- A similar name is only a merge candidate. It is never sufficient by itself to merge two clubs, players, competitions or matches.
- Provider payloads are normalized before they enter the app-facing API.
- Images are not displayed unless the exact asset's license and attribution requirements are recorded.
- A source outage must not erase the last valid data.
- No scraping of Yalla Shoot, FotMob, SofaScore or other apps/sites. Their UX patterns may inform product design; their protected data is not copied.
- KORAX remains account-free and has no fantasy game.

## Provider roles and guardrails

| Provider | Intended use | License / quota guardrail | Production status |
|---|---|---|---|
| SportScore | Live scores, fixtures, match details, incidents, lineups and supported standings | Published free tier: about 10,000 requests per IP per 24h; responses are cached for about 60 seconds; show a visible Powered by SportScore link on pages using its data | Active primary source |
| TheSportsDB | Enrichment for team/player/league/venue metadata and stable provider IDs | Free V1 key `123`; 30 requests/minute and endpoint-specific result caps. Its current terms prohibit publishing an app to an app store on the free tier; a paid subscription is required for app-store distribution. Direct APK distribution is not explicitly clarified by that clause, so do not assume broader commercial rights. Images remain unlicensed until each asset is checked | Connector implemented with quota guard and stable provider IDs; app-store release blocked until a paid plan/rights review; images remain hidden until rights review |
| OpenLigaDB | Supplemental fixtures/results/tables in leagues it actually covers | No API key; ODbL 1.0 obligations; use `getlastchangedate`; during live play do not poll a league more often than once every 30–60 seconds | Connector implemented with sport discovery and changed-date polling; first production league coverage pending |
| OpenFootball / football.json | Historical fixtures and results for the season files that exist | CC0 dataset; latest season data is updated periodically/daily; never use it as a live-score source | Connector implemented for seven confirmed 2026–27 season files; historical-only, never live |
| Wikidata | Entity identity, aliases, country, founding date, stadium links and external IDs | CC0 structured data; Query Service budget is limited (roughly 60 CPU seconds per minute and up to five concurrent queries); use batched queries and cache results | Connector implemented with batched queries and provider-specific throttle; production coverage pending first sync |
| Wikimedia Commons | Club crests, player portraits and stadium images | License is per file, not per website. Store file page, license, creator and attribution. Only verified compatible licenses may be displayed | Connector implemented with file-level license checks; only verified assets can reach the app API |
| StatsBomb Open Data | Selected historical research/analysis datasets | Research dataset; public/commercial app rights are not assumed. Attribution and logo requirements must be reviewed before use | Disabled pending explicit rights review |

Official references:

- SportScore: https://sportscore.com/developers/api/
- TheSportsDB: https://www.thesportsdb.com/documentation
- OpenLigaDB API: https://api.openligadb.de/index.html
- OpenLigaDB project: https://www.openligadb.de/
- OpenFootball: https://github.com/openfootball/football.json
- Wikidata Query Service: https://www.wikidata.org/wiki/Wikidata:SPARQL_query_service
- Wikimedia Commons licensing: https://commons.wikimedia.org/wiki/Commons:Licensing
- StatsBomb Open Data: https://github.com/hudl/open-data

## Database foundation deployed

The following internal tables now exist in Supabase:

- `korax_source_registry`: provider roles, enabled state, license/attribution flags, request quotas and health.
- `korax_source_sync_runs`: per-provider sync history, request counts and errors.
- `korax_entities`: canonical clubs, national teams, players, coaches, leagues, tournaments, stadiums and countries.
- `korax_entity_sources`: source-specific external IDs and confidence/match method.
- `korax_entity_assets`: image URL plus file-level license, creator and attribution.
- `korax_entity_facts`: field-level provenance, confidence and observed/valid time.
- `korax_external_matches`: separately stored fixtures/results from supplemental providers.
- `korax_match_source_links`: explicit mappings from supplemental match IDs to existing SportScore match slugs.
- `korax_source_coverage`: coverage by source, entity type, country, competition and season.
- `korax_entity_merge_candidates`: review queue for possible duplicates; no automatic name-only merge.

The new tables have RLS enabled. `anon` and `authenticated` have no table privileges; only `service_role` can access them. The search extensions and server-side search function are installed. Arabic normalization removes tatweel/diacritics and normalizes common alef/ya variants; PGroonga supports partial Arabic/English matching, with trigram fallback for longer typo-prone queries.

## Current status

- Implemented and deployed: schema, provider registry, license/quota metadata, source-to-entity mapping, asset/fact provenance, external-match staging, merge-candidate queue, Arabic/English search RPC and stable provider-fact upsert key.
- Tested: transactional schema creation, RLS/grants, PGroonga Arabic partial search (for example `مح` matches `محمد صلاح`), trigram fallback, provider normalization, rate-limit handling, license blocking and source-specific fixtures.
- Implemented in PR #26: provider fetch connectors, daily metadata/catalog sync, 15-minute OpenLigaDB fixture refresh, OIDC-protected sync endpoint, canonical search API and Android global search/coach profile integration. Live provider ingestion and first database counts still need post-merge verification.
- Not yet enabled: StatsBomb production ingestion, unverified TheSportsDB images, FCM push delivery.
- SportScore remains the canonical live-match source. OpenLigaDB and OpenFootball results are stored separately until an explicit match mapping is verified; no supplemental provider overwrites a live SportScore match.

## Merge policy

A cross-source merge is allowed only when a stable provider ID, Wikidata QID/external-ID relationship, or reviewed mapping supports it. Name similarity, transliteration or matching country alone creates a candidate for review; it does not merge records. Match reconciliation also requires compatible competition, participants and kickoff time. Rescheduled fixtures update an existing link instead of creating a duplicate.

## Image policy

An asset row with `license_status != 'verified'` must never be returned as a displayable image. TheSportsDB image URLs remain unverified by default. Commons metadata must include the file page URL, license code/URL, creator and required attribution before an asset can be promoted.
