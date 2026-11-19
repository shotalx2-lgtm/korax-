# Release readiness checklist

This repository's first Android client foundation is not a production release.

Before public release:
- Connect a licensed, real football-data provider through a secured backend.
- Verify data coverage, latency, attribution, image rights and commercial redistribution terms.
- Implement authenticated server-to-device push using a project-owned messaging configuration; request Android notification permission contextually.
- Finish entity profiles, match events, lineups, player stats, search and competition coverage against real source contracts.
- Add resilient caching, retry/backoff, rate limiting and a source-health dashboard.
- Add privacy policy, terms, data deletion/contact channel and Play Data Safety declarations.
- Configure an upload key and Play App Signing; never commit keystores or secrets.
- Run unit, integration, accessibility, security and device-matrix tests.
- Run backend load tests and monitoring drills. Million-user capacity cannot be claimed from an APK build alone.

A debug APK from CI is for installation/testing and is not a signed Play Store production release.
