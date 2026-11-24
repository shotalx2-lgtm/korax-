export function deriveProviderFacts(record) {
  const metadata = record && record.metadata && typeof record.metadata === "object" ? record.metadata : {};
  const values = [
    ["founded_year", metadata.formedYear],
    ["stadium", metadata.stadiumName],
    ["stadium_capacity", metadata.stadiumCapacity],
    ["official_website", metadata.website || metadata.officialWebsite],
    ["playing_position", metadata.position],
    ["height", metadata.height],
    ["weight", metadata.weight],
    ["shirt_number", metadata.shirtNumber],
    ["current_team", metadata.teamName],
    ["league", metadata.leagueName],
  ];
  return values
    .filter(([, value]) => value !== null && value !== undefined && value !== "")
    .map(([fieldKey, value]) => ({ fieldKey, value, confidence: 0.95 }));
}
