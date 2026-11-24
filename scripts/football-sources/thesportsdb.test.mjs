import test from "node:test";
import assert from "node:assert/strict";
import { theSportsDbNormalizers } from "./thesportsdb.mjs";

test("maps stable team IDs and structured club facts without trusting fan images", () => {
  const record = theSportsDbNormalizers.teamRecord({
    idTeam: "133604",
    strTeam: "Example FC",
    idWikidata: "Q12345",
    strTeamShort: "EXF",
    strAlternate: "Example Football Club",
    strCountry: "Egypt",
    strLeague: "Egyptian Premier League",
    intFormedYear: "1907",
    strStadium: "Example Stadium",
    intStadiumCapacity: "75000",
    strTeamBadge: "https://www.thesportsdb.com/images/media/team/badge/example.png",
  });
  assert.equal(record.entityType, "club");
  assert.equal(record.externalId, "133604");
  assert.equal(record.metadata.formedYear, 1907);
  assert.equal(record.metadata.wikidataId, "Q12345");
  assert.equal(record.metadata.stadiumCapacity, 75000);
  assert.equal(record.assets[0].licenseStatus, "unverified");
  assert.equal(record.assets[0].metadata.rightsReviewRequired, true);
  assert.equal("strDescriptionEN" in record.rawPayload, false);
});

test("maps player biography fields but does not invent season statistics", () => {
  const record = theSportsDbNormalizers.playerRecord({
    idPlayer: "34145937",
    strPlayer: "Example Player",
    strNationality: "Egyptian",
    dateBorn: "1992-06-15",
    strPosition: "Forward",
    strTeam: "Example FC",
    strThumb: "https://www.thesportsdb.com/images/media/player/thumb/example.png",
  });
  assert.equal(record.entityType, "player");
  assert.equal(record.birthDate, "1992-06-15");
  assert.equal(record.metadata.position, "Forward");
  assert.equal(record.assets[0].licenseStatus, "unverified");
  assert.equal(record.metadata.goals, undefined);
});

test("maps league IDs and limits free API assumptions to returned records", () => {
  const record = theSportsDbNormalizers.leagueRecord({
    idLeague: "4328",
    strLeague: "English Premier League",
    strCountry: "England",
    strSport: "Soccer",
    strCurrentSeason: "2026-2027",
  });
  assert.equal(record.entityType, "league");
  assert.equal(record.externalId, "4328");
  assert.equal(record.metadata.currentSeason, "2026-2027");
});

test("classifies coaching staff separately from playing profiles", () => {
  const record = theSportsDbNormalizers.playerRecord({
    idPlayer: "123",
    strPlayer: "Sample Assistant",
    strPosition: "Assistant Coach",
    strStatus: "Coaching",
    idWikidata: "Q67890",
  });
  assert.equal(record.entityType, "coach");
  assert.equal(record.metadata.wikidataId, "Q67890");
});
