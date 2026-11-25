import test from "node:test";
import assert from "node:assert/strict";
import { normalizeWikipediaPage } from "./wikipedia.mjs";

test("accepts an exact Wikipedia title and retains article/license attribution", () => {
  const page = normalizeWikipediaPage({
    query: { pages: {
      "123": {
        pageid: 123,
        title: "Real Madrid CF",
        fullurl: "https://en.wikipedia.org/wiki/Real_Madrid_CF",
        extract: "Real Madrid Club de Fútbol is a Spanish professional football club.",
      },
    } },
  }, "Real Madrid CF", "en");
  assert.equal(page.pageId, "123");
  assert.equal(page.language, "en");
  assert.match(page.url, /wikipedia\.org/);
  assert.equal(page.license, "CC-BY-SA-4.0/GFDL");
});

test("rejects ambiguous search results that do not exactly match the requested entity", () => {
  const page = normalizeWikipediaPage({
    query: { pages: {
      "456": { pageid: 456, title: "Real Madrid Baloncesto", extract: "Basketball team." },
    } },
  }, "Real Madrid", "en");
  assert.equal(page, null);
});

test("rejects missing pages, empty extracts and unsupported language codes", () => {
  assert.equal(normalizeWikipediaPage({ query: { pages: { "-1": { missing: "" } } } }, "Unknown", "en"), null);
  assert.equal(normalizeWikipediaPage({ query: { pages: { "1": { pageid: 1, title: "A" } } } }, "A", "en"), null);
  assert.equal(normalizeWikipediaPage({ query: { pages: { "1": { pageid: 1, title: "A", extract: "x" } } } }, "A", "de"), null);
});
