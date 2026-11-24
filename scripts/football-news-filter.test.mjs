import test from "node:test";
import assert from "node:assert/strict";
import { isFootballNewsArticle } from "../supabase/functions/korax-api/news-filter.mjs";

test("keeps Arabic football news from mixed sports publishers", () => {
  assert.equal(isFootballNewsArticle("الأهلي المصري يعلن التعاقد مع مهاجم جديد", "", "hesport"), true);
});

test("keeps international football news from mixed sports publishers", () => {
  assert.equal(isFootballNewsArticle("Real Madrid prepare for the Champions League match", "", "hesport"), true);
});

test("rejects politics and general news without football context", () => {
  assert.equal(isFootballNewsArticle("البرلمان يناقش مشروع قانون جديد", "تطورات سياسية محلية", "hesport"), false);
  assert.equal(isFootballNewsArticle("Election results announced", "Government forms a new cabinet", "hesport"), false);
});

test("generic sports words are not enough to classify an article as football", () => {
  assert.equal(isFootballNewsArticle("المدرب يعلن قائمة الفريق قبل المباراة", "مواجهة قوية وهدف حاسم", "hesport"), false);
});

test("rejects articles explicitly about other sports even when a football club is mentioned", () => {
  assert.equal(isFootballNewsArticle("Liverpool basketball team wins the final", "Basketball match report", "hesport"), false);
  assert.equal(isFootballNewsArticle("محمد صلاح يتابع بطولة التنس", "تغطية منافسات التنس", "hesport"), false);
});

test("accepts a football-specialist RSS feed with an implicit football headline", () => {
  assert.equal(isFootballNewsArticle("قرار جديد من النادي بشأن مدرب الفريق", "", "kooora"), true);
});

test("accepts publisher-specific league feeds", () => {
  assert.equal(isFootballNewsArticle("خبر من دوري مصر", "", "btolat-egypt"), true);
});
