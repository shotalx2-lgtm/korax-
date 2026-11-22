# Release readiness checklist

The current repository is an integration build, not yet a production release.

## Data source integration
- [x] Connect public SportScore REST endpoints without an API key.
- [x] Aggregate multiple Arabic and international publisher RSS feeds without a news API key.
- [x] Add clickable SportScore attribution to the matches screen.
- [x] Cache upstream responses at the edge.
- [ ] Deploy the Cloudflare Worker and set `KOORA_API_BASE_URL` in the Android release build.
- [ ] Verify all required league slugs and real response fields against live SportScore data.
- [ ] Confirm commercial use and attribution terms with SportScore.
- [ ] Verify each RSS feed's uptime, freshness, article image availability and publisher reuse terms.

## Product completeness
- [ ] SportScore does not expose a complete squad endpoint; a complete 15-player fantasy market still needs a full player-data source.
- [ ] Implement matchweek deadlines, event-based round scoring, captain multipliers, auto-substitutions, transfer limits and round locking.
- [ ] Add confirmed transfers from a provider that supplies transfer records.
- [ ] Implement cloud league rankings, friend requests, multiplayer leagues and chat if they remain in scope.
- [ ] Finish push notifications and notification backend setup.
- [ ] Configure Firebase project, Google OAuth client and Firestore rules for the actual Android app.

## Release and quality
- [ ] Configure a production upload key and Play App Signing; never commit keystores or secrets.
- [ ] Run Android unit tests and release APK build after the latest integration commit.
- [ ] Test news/match endpoints from the deployed Worker, including RSS image availability and provider rate limits.
- [ ] Install and test on physical Android devices and different screen sizes.
- [ ] Run accessibility, security, privacy and backend load tests.
- [ ] Add privacy policy, terms, data deletion/contact channel and Play Data Safety declarations.

A successful CI APK build is only a build artifact. It does not prove production readiness, live data coverage, million-user capacity or Play Store signing.
