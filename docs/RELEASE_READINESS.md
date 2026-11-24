# KORAX 0.6.8 release readiness

## Product scope confirmed

- [x] Football-only news; generic news feeds are excluded from the active Supabase news pipeline.
- [x] No login, accounts or user sessions in the Android product.
- [x] No fantasy game in the Android product or API.
- [x] Local favorites, local article interactions, offline saved summaries, and local backup/restore remain available.

## Data and app behavior

- [x] Live, scheduled and finished match endpoints use the Supabase/SportScore cache.
- [x] Match details are shown only when a non-null provider detail payload exists.
- [x] Local news notifications are filtered against selected favorites.
- [x] Team profiles are enriched with players observed in cached lineups.
- [x] Player profiles expose cached lineup presence and event counts only when supplied by SportScore.
- [x] Sync detail window expanded to 7 days back / 14 days forward, rotating 20 detail records per 15-minute cycle.
- [ ] Allow the expanded sync to run for a full day, then inspect coverage and freshness counts.
- [ ] Verify the seven current standings are refreshed on the scheduled cycle and add more only where the provider returns a valid table.
- [ ] Install and update-test the APK on physical Android devices.

## Explicit limitations

- Team roster data is only players observed in detail records; it is not a complete provider squad endpoint.
- Match lineups/events/stats may legitimately be missing.
- Background local alerts can be delayed by Android battery/network policy.
- No FCM server push, no shared accounts, no cross-device favorites/comments.
- Full protected publisher articles remain at the original publisher; KORAX presents available RSS summaries.
- No guarantee of every global league's standings, images or full statistics.

## Automated verification

The GitHub Actions Android workflow must report success for:
1. Sync batching tests and football-news worker tests.
2. Supabase API integration checks.
3. Android unit tests.
4. Release APK assembly.
5. APK signature verification.

A successful CI workflow is not a substitute for device testing.
