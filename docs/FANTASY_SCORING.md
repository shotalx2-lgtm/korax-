# KORAX Fantasy — data and scoring status

> **Not a shipped feature in the current Android main branch.** This document records experimental design notes, not a production fantasy service.

## What exists today

The current Android app does not contain a complete fantasy squad builder, round deadlines, official player market, transfer engine, captain scoring, live gameweek calculations, or multi-user fantasy leagues.

The optional/legacy Cloudflare Worker source contains a `/api/fantasy/players` prototype. Its own response metadata describes the catalogue as `top_scorers_and_assists_only` and sets `roundScoringAvailable: false`. It combines players returned by top-scorer and assist leaderboards; this is not a full league squad catalogue.

The prototype currently assigns starting prices by position:

| Position | Prototype starting price |
|---|---:|
| Goalkeeper | 4.5m |
| Defender | 5.0m |
| Midfielder | 6.0m |
| Forward | 6.5m |

These are fixed KORAX defaults, **not provider market prices**. They must not be presented to users as real market valuations.

## Experimental season formula

The prototype's `KORAX-SEASON-1` formula calculates season-total points from aggregated player statistics. It is not the official Fantasy Premier League scoring schedule and does not calculate points for individual gameweeks.

It does not currently provide verified match-by-match points, round corrections, live in-play recalculation, deadline locking, or an audited price history. Do not use season totals as round scores.

## Requirements before production

A real fantasy engine needs a verified provider and data contract for:

- Complete player rosters for each selected competition and season, with stable IDs and team membership.
- Reliable positions, squad status, shirt numbers, and player availability.
- Match-level player statistics linked to the correct fixture and gameweek.
- Confirmed goals, assists, minutes, clean sheets, saves, cards, own goals, penalty events, and corrections.
- Injury and suspension status with timestamps and source attribution.
- A price source or a transparent KORAX valuation algorithm, server-side price history, and locked prices at each deadline.
- Server-authoritative squads, budgets, transfers, captain/vice-captain rules, round totals, leaderboards, and audit logs.

No player price, injury status, or fantasy point total should be invented or inferred from a missing provider field. Provider selection must be based on a live coverage test for the exact leagues and fields required.
