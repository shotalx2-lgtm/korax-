# KOORA — Beyond Football

Android football companion app. The default launcher experience is the design-first edition.

## Design-first edition
- Modern light interface in blue, green and white.
- Arabic-first layout, five primary destinations and clean empty states.
- No fabricated match scores, teams, fixtures, player profiles or statistics.
- No login required.
- News screen reads public football RSS feeds from BBC Sport and Sky Sports directly on the device and opens each article at its publisher. Feed availability can vary by publisher/network; full articles remain on the source website.

## Limits of this APK
This is a **design preview with working RSS headlines**, not the full live-football release. Match data, teams, players, competitions, push notifications, and remote favorites are intentionally not populated. The original data-connected implementation remains in `MainActivity.kt` for the next integration phase.

## News feeds
- BBC Sport Football: https://feeds.bbci.co.uk/sport/football/rss.xml
- Sky Sports Football: https://www.skysports.com/rss/11095

The app displays headlines and short feed summaries with source attribution and links to the original article. It does not republish full article text.

## Repository
https://github.com/shotalx2-lgtm/korax-
