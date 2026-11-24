# KORAX implementation status — 24 November 2026

## Product decisions

- Android app: `com.koora.app`
- Release target: `0.6.8`, versionCode `15`
- Default branch: `main`
- No login or account system by product decision.
- No fantasy game or fantasy endpoints by product decision.
- Favorites, article interactions, comments, notifications history, offline saves, settings and backup/restore remain device-local.

## Feature status

| Area | Current state | Scope and limitation |
|---|---|---|
| News | Football-only | Arabic and English publisher RSS; server filter excludes general politics and other sports; full articles remain at publisher links |
| News details | Implemented with RSS scope | In-app title, excerpt, source, time, image when supplied, save/share and local comments |
| Match feed | Implemented with provider/cache limits | Live, date fixtures and finished matches through SportScore cache |
| Match details | Partial by source availability | Events, lineups, statistics and referee/venue only when supplied in the cached detail record |
| Competitions | Seven curated standings | Other competitions may appear in search or fixture lists without an available standings table |
| Team profiles | Partial, improved | Local cache history, competitions and players found in cached lineups; explicitly not a complete official squad |
| Player profiles | Partial, improved | Player details, lineup presence and event counts only where detail payloads exist |
| Favorites | Local and implemented | Club, national team, player and competition favorites; backup/restore is available |
| Notifications | Local alerts | Favorite-aware news alerts; match event notifications; foreground polling and WorkManager background checks |
| Login/accounts | Not included | Intentionally omitted; no account UI or user sessions |
| Fantasy | Not included | Removed from API routes and worker; no fantasy market or scoring UI |
| Cloud sync/comments | Not included | No shared cross-device data |
| FCM server push | Not included | No device-token registration or remote push delivery; local Android notifications only |

## Data snapshot before the extended detail-sync rollout

Supabase diagnostics on 24 November 2026 showed:

- 1,888 rows in `korax_sportscore_matches`.
- 66 rows with non-null `detail` payloads.
- 7 stored competition standings records.

These are a snapshot, not a promise of complete coverage. The new sync window is configured for seven days in the past and fourteen days ahead, with 20 details fetched per 15-minute cycle. New match records continue to refresh on the configured schedule. Recheck database counts and actual workflow cadence after the change has run for a full day.

## CI and release requirements

The Android workflow must pass:
- SportScore batching unit tests.
- Worker API tests, including football-only RSS and the absent fantasy route.
- Supabase API health, news, live/date fixtures, match details and standings checks.
- Android unit tests.
- Optimized Release APK build.
- APK signature verification with `apksigner`.

A successful CI run verifies the built artifact and automated tests only. It does not prove installation, upgrade behavior, battery behavior, accessibility or network reliability on every physical device.

## Known provider limitations

- SportScore does not provide full lineups/details for every fixture in the current cache.
- Player appearance counts are not inferred from incomplete data; the profile reports matches where a player was present in a cached starting lineup and event statistics seen in supplied incidents.
- Team rosters are the players observed in cached lineups, not an official complete squad list.
- Standings, injuries, suspensions, transfer confirmations and images depend on what the configured source returns.
- WorkManager is subject to Android scheduling and battery restrictions; this is not an FCM real-time push guarantee.

## Security notes

- App clients do not contain Supabase service-role credentials.
- GitHub OIDC is restricted to the dedicated SportScore sync workflow on `main`.
- Legacy contest RPC permissions are being removed because the product has no contest/fantasy flow.
