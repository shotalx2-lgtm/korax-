# KOORA — Beyond Football

Android-first football companion app. The app is designed to open without account registration.

## Product principles
- Every match, club, player and competition tile is navigable to a dedicated detail page.
- Favorites are stored locally on the device; no login is required.
- Notification preferences are scoped to selected favorites. Users with no favorites receive only a small set of general alerts when push delivery is configured.
- Real images and sports data only. Missing data stays visibly unavailable; the app must never invent scores, lineups, player photos or news.
- Clean Arabic-first interface, RTL layout, restrained copy, dark premium theme and subtle motion.

## Current implementation status
This repository is being initialized with the Android client foundation. A production deployment still requires a live licensed football-data provider, a secured backend, push-notification credentials, privacy/legal configuration, and device/load testing. Do not represent an APK as production-ready until these are complete.

## Data backend contract
Configure KOORA_API_BASE_URL at build time to a secured HTTPS backend. The Android client expects:
- GET /api/matches — current/upcoming/recent matches
- GET /api/teams — clubs and national teams
- GET /api/players — player directory
- GET /api/competitions — leagues and tournaments
- GET /api/matches/{id}, /api/teams/{id}, /api/players/{id}, /api/competitions/{id} — detail records

Each entity may include an official/licensed imageUrl or logoUrl. Do not use generated imagery as real-world sports photography.

## Build
The GitHub Actions workflow builds a debug APK on pushes and can be run manually. Release signing must be configured using repository secrets before publishing a signed release.

## Scale and reliability
Million-user readiness is a system-level property, not a UI setting. It requires load-tested backend services, caching, rate limits, observability, database capacity planning, CDN image delivery, licensed data throughput and operational ownership.