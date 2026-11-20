# KORAX — Fantasy & Football News

KORAX is an Arabic-first Android football companion with a locally persisted fantasy-team manager and live football headlines from public RSS feeds.

## Included in this build
- Native Android application using Kotlin and Jetpack Compose.
- Arabic news headlines fetched from BBC Sport Football and Sky Sports RSS feeds; articles open at their original publishers.
- Local fantasy squad management: 100m starting budget, 15-player squad limits, position quotas, a three-player-per-club cap, buying and selling, formations, captain and vice-captain selection, local points totals and transfer history.
- Fantasy roster and market entries persist on the device across app restarts.
- Clear empty states when no verified sports data is configured; the application does not create fake live scores, injuries, players or match statistics.

## Important fantasy limitations
The fantasy manager in this build is a **single-device local mode**. Market players, prices and points must be entered by the user from competition-approved data. It is not yet a connected multi-user fantasy service: accounts, Google sign-in, remote leagues, head-to-head matchmaking, cloud synchronization, administrator roles, official match-event scoring, automatic substitutions, chips, paid subscriptions and push notifications still require a deployed secure backend and service credentials.

## Football data backend
The repository includes a Cloudflare Worker adapter for Sportmonks. Provider credentials must be configured as a Cloudflare Worker secret named `SPORTMONKS_API_TOKEN`; never embed that token in the Android APK. The Worker must be deployed and its HTTPS base URL supplied as `KOORA_API_BASE_URL` at Android build time. Provider plan coverage and image redistribution rights must be verified before public use.

## Build
The GitHub Actions workflow builds a debug APK on pushes to `main` and uploads it as the `KORAX-debug-apk` workflow artifact.

## Repository
https://github.com/shotalx2-lgtm/korax-
