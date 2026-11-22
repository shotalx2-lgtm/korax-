package com.koora.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FantasyRulesTest {
    private fun player(id: String, club: String = "Club", position: String = "MID", price: Double = 5.0, points: Int = 5) =
        FantasyRulePlayer(id, club, position, price, points)

    @Test fun rejectsDuplicatePlayer() {
        val current = listOf(player("1"))
        assertFalse(FantasyRules.validateAdd(current, player("1"), 95.0).allowed)
    }

    @Test fun enforcesSquadLimit() {
        val squad = (1..15).map { player(it.toString(), "Club$it", "MID") }
        assertFalse(FantasyRules.validateAdd(squad, player("16", "Other"), 20.0).allowed)
    }

    @Test fun enforcesPositionLimit() {
        val squad = listOf(player("1", position = "GK"), player("2", position = "GK"))
        assertFalse(FantasyRules.validateAdd(squad, player("3", position = "GK"), 90.0).allowed)
    }

    @Test fun enforcesClubLimit() {
        val squad = (1..3).map { player(it.toString(), "Same Club") }
        assertFalse(FantasyRules.validateAdd(squad, player("4", "Same Club"), 80.0).allowed)
    }

    @Test fun enforcesBudget() {
        assertFalse(FantasyRules.validateAdd(emptyList(), player("1", price = 10.0), 9.99).allowed)
    }

    @Test fun validatesCaptainAndViceAreDifferentOwnedPlayers() {
        assertTrue(FantasyRules.validateCaptaincy(setOf("1", "2"), "1", "2").allowed)
        assertFalse(FantasyRules.validateCaptaincy(setOf("1", "2"), "1", "1").allowed)
        assertFalse(FantasyRules.validateCaptaincy(setOf("1"), "1", "2").allowed)
    }

    @Test fun viceCaptainGetsMultiplierOnlyWhenCaptainDoesNotAppear() {
        val squad = listOf(player("captain", points = 8), player("vice", points = 6), player("other", points = 2))
        assertEquals(18, FantasyRules.calculateRoundPoints(squad, "captain", "vice", setOf("captain", "vice", "other")))
        assertEquals(14, FantasyRules.calculateRoundPoints(squad, "captain", "vice", setOf("vice", "other")))
        assertEquals(0, FantasyRules.calculateRoundPoints(squad, "captain", "vice", emptySet()))
    }

    @Test fun chargesFourPointsPerTransferBeyondFreeAllowance() {
        assertEquals(0, FantasyRules.transferPenalty(1))
        assertEquals(4, FantasyRules.transferPenalty(2))
        assertEquals(12, FantasyRules.transferPenalty(4))
    }

    @Test fun rejectsUnknownFormation() {
        assertTrue(FantasyRules.validateFormation("4-3-3").allowed)
        assertFalse(FantasyRules.validateFormation("2-2-6").allowed)
    }
}
