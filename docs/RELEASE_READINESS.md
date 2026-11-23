# KORAX release readiness — 23 November 2026

Current reference: `main` at `20a4a92615c351d167049b3e5f63966020f0f93e`.

## Confirmed in the current codebase

- [x] Arabic and international RSS news is served through the backend with source metadata, excerpts, and publisher links.
- [x] The app has an internal news detail screen, sharing, and device-local save/like/comment interactions.
- [x] Favorites for teams, players, and competitions are saved locally and are optional at first launch.
- [x] Match, live-match, date-schedule, and competition API checks pass in CI.
- [x] Match detail responses distinguish available details from missing detail records.
- [x] The GitHub SportScore sync uses OIDC and writes through the dedicated Edge Function.
- [x] Android Worker/API integration checks and Android unit tests pass in CI.
- [x] Android Release APK build and `apksigner` verification passed in run #264. The stable release signing key was configured in that run.

## Incomplete product features

- [ ] Email/password authentication and Google sign-in.
- [ ] Shared user profiles and account-linked favorites.
- [ ] Shared comments, likes, and saved articles.
- [ ] Full fantasy game with verified complete player catalogue, real prices, round scoring, deadlines, and leaderboards.
- [ ] FCM device registration, remote delivery, and notification preferences.
- [ ] Full article reader; current in-app article view shows available RSS summary and links to the publisher.
- [ ] Complete team/player profiles, transfer history, injuries, and suspensions where a reliable provider exposes them.

## Data coverage and freshness

- [x] Supabase contains match and competition cache tables.
- [x] Seven competition standings are stored.
- [ ] Match details are not available for every match; the last diagnostic had details for only 44 of 1,631 stored matches.
- [ ] Standings and detailed match records need recurring freshness checks.
- [ ] The cron is configured for every five minutes, but the observed run history did not prove continuous five-minute execution. Run #24 was the latest scheduled run observed at 13:38 UTC; run #25 at 14:17 UTC was a push-triggered lightweight sync (50 matches, no details or standings).
- [ ] Verify RSS feed uptime, per-source image availability, provider terms, and stale-data states.
- [ ] Add or verify provider fallbacks without inventing scores, injuries, transfers, or statistics.

## Release, device, and security checks

- [x] Android Release build, unit tests, and signature verification passed in CI run #264.
- [ ] Install and test on physical Android devices, including an update from the existing installation.
- [ ] Resolve the R8 Kotlin Metadata compatibility warnings.
- [ ] Review current Node.js/Gradle GitHub Action deprecation warnings.
- [ ] Run accessibility, privacy, security, and backend load tests.
- [ ] Review the two legacy SECURITY DEFINER contest RPCs that remain executable by `authenticated` although their dependent contest tables were removed.
- [ ] Publish privacy policy, terms, and Play Data Safety declarations before a public store release.

A successful CI artifact is not the same as a final product release. It does not prove device installation, million-user capacity, complete data coverage, or Play Store readiness.
