# KORAX backend API contract

The Android client talks to the KORAX HTTPS backend. The active default is the Supabase Edge Function `korax-api`. SportScore is fetched and normalized server-side; the app does not contain a provider service credential.

## News

- `GET /api/news?per_page=40`
- Optional filters: `language=ar|en`, `category=transfers|premier-league|la-liga|serie-a|bundesliga|ligue-1|saudi|egypt|football`

Returns Arabic and English football news only. Arabic feeds include Batolat, Hesport, Kooora and Youm7 Sports alongside international English football publishers. General news RSS feeds are not included; mixed-sports feeds pass a football-specific allowlist. Non-football sport markers reject basketball, tennis, handball and other sports. Each item retains publisher name, date, summary and original link; image is included only if the feed supplies one. The in-app article page displays the available RSS excerpt and links to the publisher's full article.

## Matches

- `GET /api/matches?mode=live`
- `GET /api/matches?mode=upcoming&date=YYYY-MM-DD`
- `GET /api/matches?mode=finished&date=YYYY-MM-DD`
- `GET /api/matches?mode=all&date=YYYY-MM-DD`
- `GET /api/matches/{slug}`

Optional `scope=main` restricts the response to the curated main competitions. Detail records may be absent for a fixture; an empty detail must remain unavailable rather than being generated.

## Competitions, teams and players

- `GET /api/competitions`
- `GET /api/competitions?search=name`
- `GET /api/competitions/{slug}` — standings where cached.
- `GET /api/standings?slug={competitionSlug}&name={providerName}` — live provider standings, cached for five minutes.
- `GET /api/topscorers?slug={competitionSlug}&stat=goals|assists&limit=20&name={providerName}` — top scorers or assists, cached for five minutes.
- `GET /api/bracket?slug={competitionSlug}&name={providerName}` — knockout bracket where supported.
- `GET /api/entities/catalogue?types=league,tournament&per_page=50` — searchable provider-imported competition catalogue.
- `GET /api/teams?search=name`
- `GET /api/teams/{slug}`
- `GET /api/players?search=name`
- `GET /api/players/{slug}`

The Android statistics hub combines the competition catalogue with SportScore standings, top scorers and assists. Competition search can surface additional provider-imported or cached competitions; SportScore coverage and the provider's own competition slug support still apply. Search/profile coverage is limited by teams and players seen in cached fixtures and detailed lineups/events. Team profile rosters are the players observed in cached lineups, not an official complete squad. Player statistics reflect supplied events and cached lineups only. Missing fields remain unavailable.

## Transfers

- `GET /api/transfers`

The configured SportScore integration has no confirmed transfers endpoint. It returns an empty list with `meta.available=false`; the app may surface transfer-related publisher news, but must not invent official transfer records.

## No-login product contract

There is no login, fantasy, user session, public player market, or cross-device account data in this product. Favorites, article likes/bookmarks/comments, notification history, offline summaries and backup/restore are device-local. Background notifications are local Android notifications scheduled with WorkManager and can be delayed by system policy; there is no FCM remote-push guarantee.

## Provider attribution and rights

SportScore source attribution is displayed in the Android app. Publisher articles remain at their original source; KORAX displays only the feed's available excerpt and link. Images and statistics may legitimately be missing.
