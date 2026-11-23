package com.koora.app

internal object FootballAlertClassifier {
    fun classify(type: String, detail: String = ""): String? {
        val value = (type + " " + detail).lowercase().replace('_', ' ').replace('-', ' ').trim()
        return when {
            listOf("goal cancelled", "goal canceled", "goal disallowed", "goal overturned",
                "cancelled goal", "canceled goal", "annulled", "disallowed", "overturned",
                "إلغاء الهدف", "الغاء الهدف", "هدف ملغي", "هدف ملغى", "إلغاء").any(value::contains) -> "cancel"
            listOf("var", "video assistant", "video review", "تقنية الفيديو", "حكم الفيديو", "مراجعة الفيديو").any(value::contains) -> "var"
            listOf("assist", "assisted by", "تمريره حاسمة", "تمريرة حاسمة", "صناعة الهدف", "صانع الهدف").any(value::contains) -> "assist"
            listOf("red card", "second yellow", "طرد", "بطاقة حمراء", "إنذار ثان").any(value::contains) -> "red_card"
            listOf("yellow card", "بطاقة صفراء", "إنذار").any(value::contains) -> "yellow_card"
            listOf("penalty missed", "penalty saved", "penalty awarded", "penalty shootout",
                "ركلة جزاء", "ركلات الترجيح").any(value::contains) -> "penalty"
            listOf("goal", "own goal", "هدف", "أحرز", "سجل").any(value::contains) -> "goal"
            else -> null
        }
    }

    fun fingerprint(matchId: String, minute: String, type: String, playerId: String,
                    teamId: String, detail: String): String {
        val canonical = listOf(matchId, minute, type, playerId, teamId, detail)
            .joinToString("|").lowercase().replace(Regex("\\s+"), " ").trim()
        return canonical.hashCode().toUInt().toString(16)
    }
}
