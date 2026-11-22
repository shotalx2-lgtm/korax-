# KORAX Fantasy scoring and player data

## Supported competitions

The fantasy player catalogue is built from the current seasons of these six competitions:

- English Premier League
- Spanish La Liga
- French Ligue 1
- Italian Serie A
- German Bundesliga
- Saudi Pro League

The backend resolves each competition and current season from the provider, then retrieves each season's participating teams and team-season squads. Player images, positions, clubs, and available season statistics are returned with the catalogue. If the provider subscription does not include a competition, the API reports that league as unavailable instead of filling it with invented data.

## Initial KORAX season scoring

The first catalogue scoring model is versioned as `KORAX-SEASON-1`:

| Event/statistic | Points |
|---|---:|
| Appearance | +2 |
| Goal by goalkeeper/defender | +6 |
| Goal by midfielder | +5 |
| Goal by forward | +4 |
| Assist | +3 |
| Clean sheet by goalkeeper/defender | +4 |
| Clean sheet by midfielder | +1 |
| Every 3 goalkeeper saves | +1 |
| Penalty save | +5 |
| Penalty miss | -2 |
| Yellow card | -1 |
| Red card | -3 |
| Own goal | -2 |
| Every 2 goals conceded by goalkeeper/defender | -1 |

This is KORAX's initial custom season-total formula, not an official Premier League/FPL points schedule. The current endpoint uses provider season aggregates, so these points are season totals. Match-by-match gameweek scoring, live in-play recalculation, points corrections, and deadline locking remain separate implementation requirements before public fantasy competitions can be opened.

## Pricing

Initial prices are KORAX starting prices by position (goalkeeper 4.5m, defender 5.0m, midfielder 6.0m, forward 6.5m). They are not claimed to be official provider market valuations. A production market-price engine needs a defined price-change policy and server-side history so users cannot alter prices locally.
