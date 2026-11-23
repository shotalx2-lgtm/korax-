package com.koora.app

import java.text.Normalizer
import java.util.Locale

/**
 * Client-side safety filter for KORAX football news.
 *
 * Do not accept generic words such as "مباراة", "هدف", "مدرب", "الدوري" or
 * "منتخب" on their own: they also occur in political and non-football stories.
 * Dedicated football publishers may be trusted; mixed/general feeds require
 * an explicit football competition, organization, club or player reference.
 */
internal object FootballNewsFilter {
    private val trustedFootballSources = listOf(
        "بطولات",
        "كووورة",
        "kooora",
        "bbc sport",
        "sky sports",
        "the guardian - football"
    )

    private val footballMarkers = listOf(
        // Clear football context.
        "كرة القدم", "كره القدم", "كرة قدم", "كره قدم", "كروي", "الكروية",
        "football", "soccer", "fifa", "uefa", "caf champions league",
        "الاتحاد الدولي لكرة القدم", "الاتحاد الافريقي لكرة القدم", "الاتحاد الاوروبي لكرة القدم",
        "اتحاد الكرة", "رابطة الاندية المحترفة",

        // Competitions and leagues.
        "الدوري الانجليزي", "الدوري الاسباني", "الدوري الايطالي", "الدوري الالماني",
        "الدوري الفرنسي", "الدوري السعودي", "دوري روشن", "الدوري المصري",
        "الدوري المغربي", "دوري ابطال اوروبا", "دوري ابطال افريقيا",
        "كاس مصر لكرة القدم", "كاس العالم للاندية", "كاس العالم لكرة القدم",
        "كاس الامم الافريقية لكرة القدم", "كوبا امريكا", "كاس الاتحاد الانجليزي",
        "كاس الملك", "كاس السوبر الاسباني", "كاس السوبر الاوروبي",
        "premier league", "la liga", "serie a", "bundesliga", "ligue 1",
        "saudi pro league", "saudi professional league", "egyptian premier league",
        "champions league", "europa league", "conference league", "world cup",
        "club world cup", "fa cup", "carabao cup",

        // Clubs and national teams frequently covered by the KORAX feeds.
        "ريال مدريد", "برشلونة", "اتلتيكو مدريد", "ليفربول", "مانشستر سيتي",
        "مانشستر يونايتد", "ارسنال", "استون فيلا", "تشيلسي", "توتنهام",
        "بايرن ميونخ", "بوروسيا دورتموند", "باريس سان جيرمان", "مارسيليا",
        "يوفنتوس", "انتر ميلان", "ميلان الايطالي", "نابولي",
        "الزمالك", "بيراميدز", "الاهلي المصري", "النادي الاهلي المصري",
        "الاهلي السعودي", "الهلال السعودي", "النصر السعودي", "الاتحاد السعودي",
        "اتحاد جدة", "الشباب السعودي", "التعاون السعودي",
        "منتخب مصر لكرة القدم", "منتخب المغرب لكرة القدم", "منتخب الجزائر لكرة القدم",
        "منتخب تونس لكرة القدم", "منتخب السعودية لكرة القدم",
        "real madrid", "barcelona", "atletico madrid", "liverpool", "manchester city",
        "manchester united", "arsenal", "chelsea", "tottenham", "bayern munich",
        "borussia dortmund", "paris saint germain", "juventus", "inter milan",
        "ac milan", "napoli", "al ahly", "zamalek", "pyramids fc", "al hilal",
        "al nassr", "al ittihad",

        // Recognizable football players; avoid ambiguous surnames alone.
        "محمد صلاح", "عمر مرموش", "كيليان مبابي", "مبابي", "ليونيل ميسي",
        "كريستيانو رونالدو", "ارلينغ هالاند", "هالاند", "لامين يامال",
        "فينيسيوس جونيور", "جود بيلينغهام", "رياض محرز", "اشرف حكيمي",
        "mohamed salah", "omar marmoush", "kylian mbappe", "lionel messi",
        "cristiano ronaldo", "erling haaland", "lamine yamal"
    )

    fun isFootballNews(title: String, summary: String, source: String = ""): Boolean {
        val normalizedSource = normalize(source)
        if (normalizedSource.isNotBlank() && trustedFootballSources.any { normalizedSource.contains(normalize(it)) }) {
            return true
        }

        val content = normalize("$title $summary")
        if (content.isBlank()) return false
        return footballMarkers.any { marker ->
            val normalizedMarker = normalize(marker)
            normalizedMarker.isNotBlank() && content.contains(normalizedMarker)
        }
    }

    private fun normalize(value: String): String {
        val withoutMarks = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return withoutMarks
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ى', 'ي')
            .replace('ؤ', 'و')
            .replace('ئ', 'ي')
            .replace('ة', 'ه')
            .replace('ـ', ' ')
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }
}
