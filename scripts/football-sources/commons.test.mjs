import test from "node:test";
import assert from "node:assert/strict";
import { commonsNormalizers } from "./commons.mjs";

const value = value => ({ value });

test("verifies a CC BY portrait and preserves creator/license attribution", () => {
  const result = commonsNormalizers.normalizeCommonsPage({
    title: "File:Player.jpg",
    imageinfo: [{
      url: "https://upload.wikimedia.org/wikipedia/commons/a/a1/Player.jpg",
      descriptionurl: "https://commons.wikimedia.org/wiki/File:Player.jpg",
      width: 800,
      height: 1000,
      extmetadata: {
        LicenseShortName: value("CC BY 4.0"),
        LicenseUrl: value("https://creativecommons.org/licenses/by/4.0/"),
        UsageTerms: value("Creative Commons Attribution 4.0"),
        Artist: value("Photographer"),
        Credit: value("Original photographer"),
        AttributionRequired: value("true"),
      },
    }],
  }, [{ fileName: "Player.jpg", assetType: "portrait" }]);
  assert.equal(result.licenseStatus, "verified");
  assert.equal(result.licenseCode, "CC BY 4.0");
  assert.match(result.attributionText, /Photographer/);
  assert.equal(result.width, 800);
});

test("blocks non-commercial and no-derivatives assets", () => {
  const nc = commonsNormalizers.normalizeLicense("CC BY-NC 4.0", "Creative Commons Attribution NonCommercial");
  const nd = commonsNormalizers.normalizeLicense("CC BY-ND 4.0", "NoDerivatives");
  assert.equal(nc.allowed, false);
  assert.equal(nd.allowed, false);
});

test("keeps a licensed club logo pending trademark review", () => {
  const result = commonsNormalizers.normalizeCommonsPage({
    title: "File:Club crest.svg",
    imageinfo: [{
      url: "https://upload.wikimedia.org/wikipedia/commons/0/00/Club_crest.svg",
      descriptionurl: "https://commons.wikimedia.org/wiki/File:Club_crest.svg",
      extmetadata: {
        LicenseShortName: value("CC BY-SA 4.0"),
        LicenseUrl: value("https://creativecommons.org/licenses/by-sa/4.0/"),
        UsageTerms: value("Creative Commons Attribution ShareAlike"),
        Artist: value("Designer"),
        AttributionRequired: value("true"),
      },
    }],
  }, [{ fileName: "Club crest.svg", assetType: "logo" }]);
  assert.equal(result.licenseStatus, "unverified");
  assert.equal(result.metadata.trademarkReviewRequired, true);
});

test("matches Wikidata filenames with spaces against Commons page titles with underscores", () => {
  const result = commonsNormalizers.normalizeCommonsPage({
    title: "File:Mohamed_Salah_2018.jpg",
    imageinfo: [{
      url: "https://upload.wikimedia.org/wikipedia/commons/a/a1/Mohamed_Salah_2018.jpg",
      descriptionurl: "https://commons.wikimedia.org/wiki/File:Mohamed_Salah_2018.jpg",
      width: 640,
      height: 800,
      extmetadata: {
        LicenseShortName: value("CC0"),
        LicenseUrl: value("https://creativecommons.org/publicdomain/zero/1.0/"),
        UsageTerms: value("Public domain"),
        Artist: value("Creator")
      }
    }]
  }, [{ fileName: "Mohamed Salah 2018.jpg", assetType: "portrait", entityId: "entity-1" }]);
  assert.equal(result.entityId, "entity-1");
  assert.equal(result.fileName, "Mohamed Salah 2018.jpg");
  assert.equal(result.licenseStatus, "verified");
});

test("blocks missing or unknown licenses instead of exposing the image", () => {
  const result = commonsNormalizers.normalizeCommonsPage({
    title: "File:Unknown.jpg",
    imageinfo: [{
      url: "https://upload.wikimedia.org/wikipedia/commons/0/00/Unknown.jpg",
      descriptionurl: "https://commons.wikimedia.org/wiki/File:Unknown.jpg",
      extmetadata: { LicenseShortName: value("All rights reserved") },
    }],
  }, [{ fileName: "Unknown.jpg", assetType: "stadium" }]);
  assert.equal(result.licenseStatus, "blocked");
});

test("blocks short-form NC and ND license identifiers even without long usage text", () => {
  assert.equal(commonsNormalizers.normalizeLicense("CC BY-NC 4.0", "").allowed, false);
  assert.equal(commonsNormalizers.normalizeLicense("CC BY-ND 4.0", "").allowed, false);
  assert.equal(commonsNormalizers.normalizeLicense("CC BY-SA 4.0", "").allowed, true);
});
