# KORAX implementation status — 23 November 2026

## Snapshot

- Repository: `shotalx2-lgtm/korax-`
- Branch: `main`
- Reference commit: `20a4a92615c351d167049b3e5f63966020f0f93e`
- Android package/version: `com.koora.app`, `0.6.2`, versionCode `9`
- Current active Android API default: Supabase Edge Function `korax-api`
- SportScore sync Edge Function: version 8, with GitHub OIDC restricted to the dedicated sync workflow on `main`

## Feature status

| Area | Status | Verified state |
|---|---|---|
| News feed | Partial | Arabic and English publisher RSS aggregation, deduplication, cache, source/date/summary, image when the feed supplies one |
| News details | Partial | Internal detail screen, share/save/comment controls; content is the available RSS summary, with publisher link for the full article |
| Favorites | Partial | Team/player/competition favorites persist locally; not account-linked or synced across devices |
| Match list/live/schedule | Implemented with source limits | Supabase-backed SportScore match cache; API checks passed |
| Match details | Partial | Only rows with detail payloads have extra content; provider fields may be empty or unavailable |
| Competitions/standings | Partial | Seven competition tables stored; freshness must be verified regularly |
| Team/player data | Partial | Search/profile endpoints exist, but complete squads and consistent profile coverage are not guaranteed |
| Local alerts | Partial | WorkManager polls in the background; Android may defer it, and it is not a remote real-time push service |
| Accounts | Missing | No active email/password or Google sign-in flow in current Android main |
| Shared comments/favorites | Missing | Backend groundwork exists, but Android uses local storage for these interactions |
| FCM remote push | Missing | No Android FCM SDK, token registration, or Firebase messaging service in current main |
| Fantasy | Missing | Experimental Worker catalogue only; no complete player market or live round scoring |

## Match-data snapshot

At the last database diagnostic on 23 November 2026:

- 1,631 match rows.
- 44 rows with a non-null detail payload.
- 9 live matches at that instant.
- 7 competition standings records.

After the successful push-triggered sync run #25, the match table's latest general update advanced to 14:17:26 UTC. That run collected and stored 50 matches, zero match details, and zero standings tables. Detail data was last updated at 12:16:02 UTC; standings were last updated at 12:08:39 UTC.

The workflow schedule is configured as `*/5 * * * *`, but the observed GitHub run history did not demonstrate uninterrupted five-minute execution. Do not label the full dataset as continuously fresh until scheduled runs are observed and audited.

## CI and release verification

Android Build #264 completed successfully on the reference commit:

- Worker checks passed.
- Supabase API health, live matches, date fixtures, a detail response, and competition standings passed.
- Android unit tests passed.
- Release APK build passed.
- `apksigner` verification passed.
- Stable signing key was configured.
- Artifact `KORAX-optimized-apk` was uploaded.

The APK was compared with the previous successful build (#255). Both contain 140 ZIP entries; the only changed entry is `META-INF/version-control-info.textproto`, which records the new commit. The signing certificate SHA-256 fingerprint is unchanged. This is a pipeline-verified rebuild of the same application code, not a feature release.

R8 Kotlin Metadata warnings remain. No physical-device installation, upgrade test, or load test has been performed.

## Backend and security notes

- Supabase public tables have RLS enabled and no broad public table policies; current app access is through Edge Functions.
- The two legacy SECURITY DEFINER functions `join_korax_contest` and `submit_korax_prediction` remain executable by `authenticated` although the contest tables they reference are absent. Review/revoke obsolete grants through a migration after checking dependencies.
- `korax_push_devices` had zero registered devices; `korax_push_outbox` had 21 pending rows and zero sent rows at the diagnostic snapshot.
- The existing push sender expects an old `korax-sync.yml` workflow that is not present in current `main`. The Android app also lacks FCM registration/receive code.
- Cloudflare's `korax` Worker build succeeded on the snapshot commit; the separate `korax-sports-proxy` build failed. The Android app's default API remains Supabase. `CLOUDFLARE_API_TOKEN` is used only by the separate Cloudflare Worker deploy workflow.

## Safe next implementation order

1. Confirm scheduled sync cadence and detail/standings refresh behavior.
2. Resolve the legacy security-definer RPC grants and reconcile the documentation/configuration.
3. Select and live-test a provider that supplies full squads, match-level player stats, and availability data before coding fantasy.
4. Implement one account system (prefer evaluate Supabase Auth before adding Firebase Auth) and then account-linked favorites/comments.
5. Add FCM only after device-token registration and end-to-end delivery can be tested.
6. Implement fantasy scoring/price history on the server after the source contract is proven.
7. Run physical-device, upgrade, offline, and load tests before calling any APK a final release.
