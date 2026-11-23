package com.koora.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FootballAlertClassifierTest {
    @Test fun classifiesGoal() { assertEquals("goal", FootballAlertClassifier.classify("Goal")) }
    @Test fun classifiesAssist() { assertEquals("assist", FootballAlertClassifier.classify("Assist")) }
    @Test fun classifiesVarReview() { assertEquals("var", FootballAlertClassifier.classify("VAR review")) }
    @Test fun cancellationTakesPriorityOverVar() {
        assertEquals("cancel", FootballAlertClassifier.classify("Goal cancelled by VAR"))
    }
    @Test fun classifiesCardsAndPenalty() {
        assertEquals("red_card", FootballAlertClassifier.classify("Red Card"))
        assertEquals("yellow_card", FootballAlertClassifier.classify("Yellow Card"))
        assertEquals("penalty", FootballAlertClassifier.classify("Penalty missed"))
    }
    @Test fun eventFingerprintIsStableForTheSameEvent() {
        val first = FootballAlertClassifier.fingerprint("match-1", "67", "Goal", "p-1", "t-1", "Header")
        val second = FootballAlertClassifier.fingerprint("match-1", "67", "Goal", "p-1", "t-1", "Header")
        assertEquals(first, second)
    }
}
