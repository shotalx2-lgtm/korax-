# KORAX — Arabic Football & Fantasy

KORAX is an Arabic-first native Android football app built with Kotlin and Jetpack Compose.

## Current app build
- Arabic football news from Youm7, Alikhbariya and Google News RSS feeds, with source attribution, publisher article links, image extraction where supplied by the feed, and a local cache for offline viewing.
- A Cloudflare Worker endpoint at `GET /api/news` aggregates and edge-caches Arabic news.
- Fantasy squad management on the device: 100m game budget, 15-player squad rules, position quotas, a three-player-per-club limit, transfers, formations, captain/vice-captain and local history.
- When the football backend is configured, the Fantasy market can import up to 50 real player records from the provider. Initial game prices are KORAX defaults, not official market prices; points are not official until a season-scoped statistics/scoring service is connected.
- News uses a lazy list and cropped, on-demand images. Hardware acceleration is enabled. Network work runs off the UI thread and news is cached locally to reduce loading and stutter on lower-end phones.

## Important limitations
This is not yet a production multi-user fantasy platform. Fantasy teams and transfer history are still stored locally on the device. Accounts, Google sign-in, shared leagues, leaderboards, head-to-head play, authoritative season player prices and points, live fantasy scoring, automatic substitutions, chips, push notifications, payments, administrator roles and cloud synchronization still need a deployed authenticated backend and persistent database.

A build or passing test does not prove capacity for millions of concurrent users. That requires production infrastructure configuration, load tests, monitoring, rate limiting, backups, incident recovery and a provider subscription with the required competitions and statistics.

## Football data backend
The repository includes a Cloudflare Worker adapter for Sportmonks and the Arabic RSS news aggregator. Deploy the Worker and configure:
- `SPORTMONKS_API_TOKEN` as a Worker secret (never place it in the APK).
- `KOORA_API_BASE_URL` as a GitHub Actions secret containing the Worker's HTTPS base URL.
- A Sportmonks subscription covering the leagues and player statistics used by the product.

Check image rights and commercial redistribution terms for all provider content before public release.

## Android build
GitHub Actions builds an optimized, minified release APK for testing. CI signs it with the Android debug certificate; it is installable for testing but is not a Play Store production-signed release.

## Repository
https://github.com/shotalx2-lgtm/korax-
