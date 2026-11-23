package com.koora.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballNewsFilterTest {
    @Test fun acceptsArabicClubAndLeagueNews() {
        assertTrue(FootballNewsFilter.isFootballNews("تشكيل الزمالك الرسمي أمام الجونة", "مباراة في الدوري المصري"))
        assertTrue(FootballNewsFilter.isFootballNews("ريال مدريد يعلن قائمته", "قبل مواجهة في الدوري الإسباني"))
        assertTrue(FootballNewsFilter.isFootballNews("الهلال السعودي يضم لاعبًا جديدًا", "صفقة في دوري روشن"))
    }

    @Test fun acceptsRecognizedFootballPlayersAndCompetitions() {
        assertTrue(FootballNewsFilter.isFootballNews("محمد صلاح يتألق مع ليفربول", "في الدوري الإنجليزي"))
        assertTrue(FootballNewsFilter.isFootballNews("UEFA Champions League: draw announced", ""))
        assertTrue(FootballNewsFilter.isFootballNews("عمر مرموش يسجل هدفًا", ""))
    }

    @Test fun trustsDedicatedFootballPublishers() {
        assertTrue(FootballNewsFilter.isFootballNews("خبر عن قائمة المنتخب", "", "كووورة"))
        assertTrue(FootballNewsFilter.isFootballNews("Latest team update", "", "BBC Sport"))
    }

    @Test fun rejectsPoliticalAndGeneralStoriesFromMixedFeeds() {
        assertFalse(FootballNewsFilter.isFootballNews("الحكومة تعلن تشكيل لجنة جديدة", ""))
        assertFalse(FootballNewsFilter.isFootballNews("الرئيس يبحث مع الوزراء جدول الأعمال", "اجتماع رسمي"))
        assertFalse(FootballNewsFilter.isFootballNews("المنتخب الحكومي يعلن تشكيلته", ""))
        assertFalse(FootballNewsFilter.isFootballNews("هدف جديد في خطة التنمية", ""))
        assertFalse(FootballNewsFilter.isFootballNews("مدرب التنمية يعلن تفاصيل المبادرة", ""))
    }

    @Test fun genericFootballWordsDoNotPassByThemselves() {
        assertFalse(FootballNewsFilter.isFootballNews("مباراة مهمة غدًا", ""))
        assertFalse(FootballNewsFilter.isFootballNews("المدرب يعلن التشكيل", ""))
        assertFalse(FootballNewsFilter.isFootballNews("الدوري يواجه تحديات جديدة", ""))
        assertFalse(FootballNewsFilter.isFootballNews("هدف جديد للحكومة", ""))
        assertFalse(FootballNewsFilter.isFootballNews("إصابة مسؤول في حادث", ""))
    }
}
