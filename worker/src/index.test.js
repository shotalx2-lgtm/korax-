import test from "node:test";
import assert from "node:assert/strict";
import { normalizeFixture } from "./index.js";

test("normalizeFixture extracts the stable SportScore match slug from its URL", () => {
  const fixture = normalizeFixture({
    home: "SV River Plate",
    away: "SV Britannia",
    home_logo: "https://img.thesports.com/football/team/home.png",
    away_logo: "https://img.thesports.com/football/team/away.png",
    home_score: "0",
    away_score: "6",
    status: "finished",
    status_text: "Finished",
    time: "2026-11-23T00:00:00+00:00",
    competition: "Aruba Division Di Honor",
    url: "/football/match/sv-britannia-vs-sv-river-plate/318q66h8o4opqo9/",
  });

  assert.equal(fixture.id, "318q66h8o4opqo9");
  assert.equal(fixture.homeTeam.name, "SV River Plate");
  assert.equal(fixture.awayTeam.name, "SV Britannia");
  assert.equal(fixture.homeScore, 0);
  assert.equal(fixture.awayScore, 6);
  assert.equal(fixture.competitionName, "Aruba Division Di Honor");
});
