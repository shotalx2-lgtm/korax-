import test from "node:test";
import assert from "node:assert/strict";
import { deriveProviderFacts } from "./facts.mjs";

test("extracts only confirmed metadata fields as source-attributed facts", () => {
  const facts = deriveProviderFacts({
    metadata: {
      formedYear: 1907,
      stadiumName: "Cairo International Stadium",
      stadiumCapacity: 75000,
      website: "https://example.test",
      position: null,
      height: "",
      teamName: "Al Ahly"
    }
  });
  assert.deepEqual(facts.map(fact => fact.fieldKey), [
    "founded_year", "stadium", "stadium_capacity", "official_website", "current_team"
  ]);
  assert.equal(facts.find(fact => fact.fieldKey === "stadium_capacity").value, 75000);
  assert.equal(facts.every(fact => fact.confidence === 0.95), true);
});

test("returns no facts when the provider did not provide any", () => {
  assert.deepEqual(deriveProviderFacts({ metadata: {} }), []);
  assert.deepEqual(deriveProviderFacts({}), []);
});
