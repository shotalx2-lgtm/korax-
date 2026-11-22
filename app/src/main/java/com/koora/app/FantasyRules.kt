package com.koora.app

/**
 * Pure fantasy-game rules shared by the Android UI and automated tests.
 * Provider data and official scoring values must be supplied by the backend;
 * this class never invents player statistics.
 */
data class FantasyRulePlayer(
    val id: String,
    val club: String,
    val position: String,
    val price: Double,
    val points: Int = 0
)

data class FantasyRuleResult(
    val allowed: Boolean,
    val reason: String = ""
)

object FantasyRules {
    const val SQUAD_SIZE = 15
    const val STARTING_XI_SIZE = 11
    const val STARTING_BUDGET = 100.0
    const val MAX_PER_CLUB = 3
    const val EXTRA_TRANSFER_COST = 4

    val positionLimits = mapOf("GK" to 2, "DEF" to 5, "MID" to 5, "FWD" to 3)
    val formations = setOf("4-3-3", "4-4-2", "3-4-3", "3-5-2", "4-5-1", "5-3-2", "5-4-1")

    fun validateAdd(
        current: List<FantasyRulePlayer>,
        candidate: FantasyRulePlayer,
        remainingBudget: Double
    ): FantasyRuleResult {
        if (candidate.id.isBlank()) return FantasyRuleResult(false, "معرّف اللاعب غير صالح.")
        if (current.any { it.id == candidate.id }) return FantasyRuleResult(false, "اللاعب موجود بالفعل في فريقك.")
        if (candidate.position !in positionLimits) return FantasyRuleResult(false, "مركز اللاعب غير معروف.")
        if (!candidate.price.isFinite() || candidate.price <= 0.0) return FantasyRuleResult(false, "سعر اللاعب غير صالح.")
        if (current.size >= SQUAD_SIZE) return FantasyRuleResult(false, "اكتمل الفريق: الحد الأقصى 15 لاعبًا.")
        if (current.count { it.position == candidate.position } >= positionLimits.getValue(candidate.position)) {
            return FantasyRuleResult(false, "اكتمل العدد المسموح لهذا المركز.")
        }
        if (candidate.club.isNotBlank() && current.count { it.club.equals(candidate.club, true) } >= MAX_PER_CLUB) {
            return FantasyRuleResult(false, "الحد الأقصى ثلاثة لاعبين من النادي الواحد.")
        }
        if (candidate.price > remainingBudget + 0.0001) return FantasyRuleResult(false, "الميزانية المتبقية لا تكفي.")
        return FantasyRuleResult(true)
    }

    fun validateCaptaincy(ownedIds: Set<String>, captainId: String, viceId: String): FantasyRuleResult {
        if (captainId.isBlank() || captainId !in ownedIds) return FantasyRuleResult(false, "اختر قائدًا من لاعبي فريقك.")
        if (viceId.isBlank() || viceId !in ownedIds) return FantasyRuleResult(false, "اختر نائب قائد من لاعبي فريقك.")
        if (captainId == viceId) return FantasyRuleResult(false, "لا يمكن اختيار اللاعب نفسه قائدًا ونائبًا.")
        return FantasyRuleResult(true)
    }

    fun validateFormation(formation: String): FantasyRuleResult =
        if (formation in formations) FantasyRuleResult(true)
        else FantasyRuleResult(false, "الخطة المختارة غير مدعومة.")

    /**
     * Captain receives the multiplier only if he appeared. If he did not,
     * the vice-captain receives it when he appeared. Unplayed players score zero.
     */
    fun calculateRoundPoints(
        players: List<FantasyRulePlayer>,
        captainId: String,
        viceId: String,
        appearedIds: Set<String>
    ): Int {
        val base = players.sumOf { if (it.id in appearedIds) it.points else 0 }
        val multiplierId = when {
            captainId in appearedIds -> captainId
            viceId in appearedIds -> viceId
            else -> ""
        }
        val multiplierPoints = players.firstOrNull { it.id == multiplierId }?.points ?: 0
        return base + multiplierPoints
    }

    fun transferPenalty(transfersMade: Int, freeTransfers: Int = 1): Int =
        (transfersMade - freeTransfers).coerceAtLeast(0) * EXTRA_TRANSFER_COST

    fun remainingBudget(budget: Double, players: List<FantasyRulePlayer>): Double =
        budget - players.sumOf { it.price }
}
