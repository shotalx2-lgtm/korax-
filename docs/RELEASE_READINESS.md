# KORAX release readiness checklist

KORAX is currently focused on a public football experience: Arabic and international news, live and scheduled matches, competitions, standings, results, teams, players, statistics and transfers where reliable data is available.

## Current product scope
- [x] Remove sign-in and account screens from the current app experience; accounts may be considered in a future release.
- [x] Remove the fantasy game from the current app experience.
- [ ] Expand the home/news experience with clear categories, search, refresh and article images.
- [ ] Complete live, upcoming and finished match coverage with match details and event timelines where supplied.
- [ ] Add competition pages with tables, fixtures, results and top scorers.
- [ ] Add team and player profiles using verified provider data.
- [ ] Add reliable favorites and local notification preferences without requiring an account.

## Data source integration
- [x] Connect public SportScore REST endpoints without an API key.
- [x] Aggregate Arabic and international publisher RSS feeds without a news API key.
- [x] Cache upstream responses at the edge.
- [ ] Deploy the latest Cloudflare Worker changes and verify the production endpoints.
- [ ] Verify league identifiers, response fields, coverage and freshness against live provider data.
- [ ] Confirm commercial use and attribution terms with each provider and publisher.
- [ ] Verify RSS uptime, article image availability and content reuse terms.
- [ ] Add provider fallbacks and clear stale-data/error states; never fabricate scores, tables or transfers.

## Release and quality
- [ ] Configure a production upload key and Play App Signing.
- [ ] Run Android unit tests and optimized release build after each integration change.
- [ ] Test news, matches, competitions and images against the deployed Worker.
- [ ] Install and test on physical Android devices and different screen sizes.
- [ ] Run accessibility, security, privacy and backend load tests.
- [ ] Add privacy policy, terms and Play Data Safety declarations.

A successful CI APK build is only a build artifact. It does not prove production readiness, complete data coverage, million-user capacity or Play Store signing.
