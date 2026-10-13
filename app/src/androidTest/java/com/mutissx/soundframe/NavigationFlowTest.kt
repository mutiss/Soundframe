package com.mutissx.soundframe

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mutissx.soundframe.app.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Onboarding (if shown) → Studio → Gallery → Settings through the floating navigation.
 * The artworks animate forever, so the test drives the frame clock manually instead of waiting
 * for Compose to become idle.
 */
@RunWith(AndroidJUnit4::class)
class NavigationFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun pauseClock() {
        rule.mainClock.autoAdvance = false
    }

    private fun exists(matcher: SemanticsMatcher) =
        rule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    private fun advanceUntil(matcher: SemanticsMatcher, timeoutMs: Long = 10_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!exists(matcher)) {
            if (System.currentTimeMillis() > deadline) {
                rule.onRoot(useUnmergedTree = true).printToLog("SF_TREE")
                error("Timed out waiting for ${matcher.description}")
            }
            rule.mainClock.advanceTimeBy(100)
            Thread.sleep(20)
        }
    }

    @Test
    fun mainDestinationsAreReachable() {
        advanceUntil(hasText("Skip") or hasContentDescription("Start recording"))
        if (exists(hasText("Skip"))) rule.onNodeWithText("Skip").performClick()
        advanceUntil(hasText("Import an audio file"))

        rule.onNodeWithContentDescription("Gallery").performClick()
        advanceUntil(hasText("Newest"))

        rule.onNodeWithContentDescription("Settings").performClick()
        advanceUntil(hasText("Private by design"))
        advanceUntil(hasText("Delete all creations"))
    }
}
