package com.koora.app

import java.text.Normalizer
import java.util.Locale

/**
 * Client-side safety filter for KORAX football news.
 *
 * Mixed publisher feeds are accepted only when their title/summary contains
 * football-specific context. Generic sports words such as "match", "coach",
 * "goal" or "team" are never enough on their own.
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

    private val nonFootballMarkers = listOf(
        "كرة السلة", "كره السله", "السلة", "السله", "basketball",
        "كرة اليد", "كره اليد", "handball",
        "الكرة الطائرة", "كره الطائره", "volleyball",
        "التنس", "tennis",
        "كرة الصالات", "كره الصالات", "futsal",
        "الفورمولا 1", "فورمولا 1", "formula 1", "formula one",
        "سباقات السيارات", "motorsport", "motorsports",
        "كريكيت", "cricket",
        "ملاكمة", "boxing", "ufc", "mma", "فنون قتالية", "رياضات قتالية",
        "مصارعة", "wrestling",
        "سباحة", "swimming",
        "ألعاب القوى", "العاب القوي", "athletics",
        "دراجات هوائية", "cycling",
        "كرة الطاولة", "table tennis",
        "الاسكواش", "squash",
        "الريشة الطائرة", "badminton"
    )

    private val footballMarkers = listOf(
        // Clear football context.
        "كرة القدم", "كره القدم", "كرة قدم", "كره قدم", "كروي", "الكروية",
        "football", "soccer", "fifa", "uefa", "caf champions league",
        "الاتحاد الدولي لكرة القدم", "الاتحاد الافريقي لكرة القدم", "الاتحاد الاوروبي لكرة القدم",
        "اتحاد الكرة", "رابطة الاندية المحترفة",

        // Football competitions and leagues.
        "الدوري الانجليزي", "الدوري الاسباني", "الدوري الايطالي", "الدوري الالماني",
        "الدوري الفرنسي", "الدوري السعودي", "دوري روشن", "الدوري المصري",
        "الدوري المغربي", "دوري ابطال اوروبا", "دوري ابطال افريقيا",
        "كاس مصر لكرة القدم", "كاس العالم للاندية", "كاس العالم لكرة القدم",
        "كاس الامم الافريقية لكرة القدم", "كوبا امريكا", "كاس الاتحاد الانجليزي",
        "كاس الملك", "كاس السوبر الاسباني", "كاس السوبر الاوروبي",
        "premier league", "la liga", "serie a", "bundesliga", "ligue 1",
        "saudi pro league", "saudi professional league", "egyptian premier league",
        "uefa champions league", "afc champions league", "caf champions league", "europa league", "conference league",
        "club world cup", "fa cup", "carabao cup"
    )

    private val footballContextMarkers = listOf(
        "مباراة", "مواجه", "يلتقي", "يواجه", "فاز", "يفوز", "خسر", "يخسر",
        "انتصار", "هزيم", "تعادل", "هدف", "اهداف", "تشكيل", "تشكيله",
        "قائمه الفريق", "قائمه المنتخب", "مدرب", "مدير فني", "لاعب",
        "مهاجم", "مدافع", "حارس مرمي", "جناح", "صفقه", "انتقال", "تعاقد",
        "اعاره", "تجديد عقد", "الشوط", "ركله جزاء", "تصفيات", "ودي",
        "match", "fixture", "lineup", "squad", "coach", "manager",
        "goal", "goals", "win", "beat", "defeat", "draw", "transfer",
        "signs", "signing", "striker", "forward", "defender", "goalkeeper",
        "kickoff", "qualifier", "friendly", "champion"
    )

    private val clubsAndPlayers = listOf(
        // Clubs and national teams.
        "ريال مدريد", "برشلونة", "اتلتيكو مدريد", "ليفربول", "مانشستر سيتي",
        "مانشستر يونايتد", "ارسنال", "استون فيلا", "تشيلسي", "توتنهام",
        "بايرن ميونخ", "بوروسيا دورتموند", "باريس سان جيرمان", "مارسيليا",
        "يوفنتوس", "انتر ميلان", "ميلان الايطالي", "نابولي",
        "الزمالك", "بيراميدز", "الاهلي المصري", "النادي الاهلي المصري",
        "الاهلي السعودي", "الهلال السعودي", "النصر السعودي", "الاتحاد السعودي",
        "اتحاد جدة", "الشباب السعودي", "التعاون السعودي",
        "منتخب مصر", "منتخب المغرب", "منتخب الجزائر", "منتخب تونس", "منتخب السعودية",
        "real madrid", "barcelona", "atletico madrid", "liverpool", "manchester city",
        "manchester united", "arsenal", "chelsea", "tottenham", "bayern munich",
        "borussia dortmund", "paris saint germain", "juventus", "inter milan",
        "ac milan", "napoli", "al ahly", "zamalek", "pyramids fc", "al hilal",
        "al nassr", "al ittihad",

        // Recognizable football players.
        "محمد صلاح", "عمر مرموش", "كيليان مبابي", "مبابي", "ليونيل ميسي",
        "كريستيانو رونالدو", "ارلينغ هالاند", "هالاند", "لامين يامال",
        "فينيسيوس جونيور", "جود بيلينغهام", "رياض محرز", "اشرف حكيمي",
        "mohamed salah", "omar marmoush", "kylian mbappe", "lionel messi",
        "cristiano ronaldo", "erling haaland", "lamine yamal"
    )

    fun isFootballNews(title: String, summary: String, source: String = ""): Boolean {
        val content = normalize("$title $summary")
        if (content.isBlank()) return false

        // A basketball/tennis/other-sport article from a mixed feed must not
        // leak into the football feed even if a football club is mentioned.
        if (nonFootballMarkers.any { containsMarker(content, it) }) return false

        val normalizedSource = normalize(source)
        if (normalizedSource.isNotBlank() &&
            trustedFootballSources.any { normalizedSource.contains(normalize(it)) }
        ) return true

        if (footballMarkers.any { containsMarker(content, it) }) return true

        val mentionsFootballEntity = clubsAndPlayers.any { containsMarker(content, it) }
        val hasFootballContext = footballContextMarkers.any { containsMarker(content, it) }
        return mentionsFootballEntity && hasFootballContext
    }

    private fun containsMarker(content: String, marker: String): Boolean {
        val value = normalize(marker)
        return value.isNotBlank() && (" $content ").contains(" $value ")
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
