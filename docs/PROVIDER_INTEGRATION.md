# Provider integration

KORAX uses two independent no-key source families behind its Cloudflare Worker.

## SportScore football data

- Official docs: https://sportscore.com/developers/api/
- Public REST base: https://sportscore.com
- No API key required for the public attribution tier.
- Available documented routes include fixtures, live/recent matches, match detail, team schedule, standings, top scorers/assists, player profiles and search.
- The provider documents approximately 10,000 requests per 24 hours per IP for anonymous public use; confirm current terms and fair-use limits before launch.
- Required visible “Powered by SportScore” backlink attribution is added to the Android matches screen.

The current provider documentation does not expose a complete league squad catalogue or a confirmed transfer feed. KORAX's fantasy endpoint therefore returns only players appearing in top-scorer/assist leaderboards; it must not be described as a full player market or live gameweek scoring. The transfer endpoint is explicitly marked unavailable.

## Publisher RSS news

The Worker reads RSS feeds from بطولات, اليوم السابع, هسبورت, الجزيرة, سكاي نيوز عربية, BBC Sport, Sky Sports and The Guardian. No news API key is used. The Worker keeps short headlines/summaries, original article links, source labels, timestamps, and feed-provided image URLs when available.

RSS refresh and article/image availability depend on each publisher. Check publisher terms, image licensing and commercial reuse permissions before public monetization. Never copy full article bodies.

## Operational notes

- The Android app contains no provider secret.
- Edge caching reduces repeated upstream requests.
- Keep a source-health check for each feed and each SportScore endpoint.
- Do not claim all competitions, player profiles, images, lineups or statistics are available until verified against live responses.
