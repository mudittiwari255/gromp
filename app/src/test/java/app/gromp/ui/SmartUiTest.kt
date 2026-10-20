package app.gromp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ApplicationProvider
import app.gromp.AppContainer
import app.gromp.ui.components.LocalContainer
import org.robolectric.annotation.Config
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.gromp.data.TodaySmart
import app.gromp.lock.LockMode
import app.gromp.lock.LockRequest
import app.gromp.lock.LockScreen
import app.gromp.smart.Copy
import app.gromp.smart.DayTotal
import app.gromp.smart.IntentKind
import app.gromp.smart.LongSession
import app.gromp.smart.ReviewInput
import app.gromp.smart.TaperDecision
import app.gromp.smart.Trade
import app.gromp.smart.Voice
import app.gromp.smart.WeakSpot
import app.gromp.smart.WeeklyReview
import app.gromp.ui.screens.RealCostCard
import app.gromp.ui.screens.ReviewContent
import app.gromp.ui.screens.WorthCard
import app.gromp.ui.theme.GrompTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Renders the new screens on the JVM and checks what a person would see and tap. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class SmartUiTest {
    @get:Rule val rule = createComposeRule()

    private val min = 60_000L
    private val h = 60 * min

    @Test
    fun `binge check-in shows the copy, the cost and both choices`() {
        var left = false
        var stayed = false
        rule.setContent {
            GrompTheme {
                LockScreen(
                    request = LockRequest("com.instagram.android", LockMode.Binge, minutes = 22, cost = "a workout"),
                    label = "Instagram", icon = null, snoozesToday = 0, voice = Voice.Honest,
                    onLeave = { left = true }, onContinue = { stayed = true },
                )
            }
        }
        val c = Copy.binge("Instagram", 22, "a workout", Voice.Honest)
        rule.onNodeWithText(c.title).assertIsDisplayed()
        rule.onNodeWithText(c.body).assertIsDisplayed()
        rule.onNodeWithText(c.leave).performClick()
        rule.onNodeWithText(c.stay).performClick()
        assertTrue(left && stayed)
    }

    @Test
    fun `intention screen offers every reason and reports the choice`() {
        var picked: IntentKind? = null
        rule.setContent {
            GrompTheme {
                LockScreen(
                    request = LockRequest("com.instagram.android", LockMode.Intention),
                    label = "Instagram", icon = null, snoozesToday = 0, voice = Voice.Tough,
                    trades = listOf(Trade.Reading), todayMs = 2 * h,
                    onLeave = {}, onContinue = {}, onIntention = { picked = it },
                )
            }
        }
        rule.onNodeWithText(Copy.intention("Instagram", Voice.Tough).title).assertIsDisplayed()
        IntentKind.entries.forEach { rule.onNodeWithText(it.label).assertIsDisplayed() }
        // Tough love shows the cost before you go in.
        rule.onNodeWithText("2h on Instagram. You traded 6 chapters of a book for this.").assertIsDisplayed()
        rule.onNodeWithText(IntentKind.Reply.label).performClick()
        assertEquals(IntentKind.Reply, picked)
    }

    @Test
    fun `drift and loop check-ins render`() {
        rule.setContent {
            GrompTheme {
                LockScreen(
                    request = LockRequest("a", LockMode.Drift, minutes = 12, intent = IntentKind.Reply),
                    label = "WhatsApp", icon = null, snoozesToday = 0, voice = Voice.Gentle, onLeave = {}, onContinue = {},
                )
            }
        }
        rule.onNodeWithText(Copy.drift("WhatsApp", IntentKind.Reply, 12, Voice.Gentle).body).assertIsDisplayed()
    }

    @Test
    fun `limit screen adds the real cost`() {
        rule.setContent {
            GrompTheme {
                LockScreen(
                    request = LockRequest("a", LockMode.Limit, limitMin = 60, usedMs = 61 * min),
                    label = "Instagram", icon = null, snoozesToday = 0, voice = Voice.Honest, trades = listOf(Trade.Exercise),
                    onLeave = {}, onContinue = {},
                )
            }
        }
        rule.onNodeWithText("1h 1m on Instagram today. That's a workout.").assertIsDisplayed()
    }

    @Test
    fun `real cost card asks for trades when none are chosen`() {
        var asked = false
        rule.setContent {
            GrompTheme { RealCostCard(TodaySmart("4h on your phone today.", "At 4h a day…", null, emptyList(), false)) { asked = true } }
        }
        rule.onNodeWithText("4h on your phone today.").assertIsDisplayed()
        rule.onNodeWithText("Choose what matters").performClick()
        assertTrue(asked)
    }

    @Test
    fun `worth card rates a session`() {
        var result: Pair<LongSession, Boolean>? = null
        val s = LongSession("com.instagram.android", 1_000_000, 1_000_000 + 25 * min)
        rule.setContent {
            val container = AppContainer(ApplicationProvider.getApplicationContext())
            CompositionLocalProvider(LocalContainer provides container) {
                GrompTheme { WorthCard(listOf(s), { "Instagram" }) { session, worth -> result = session to worth } }
            }
        }
        rule.onNodeWithText("Not worth it").performClick()
        assertEquals(s to false, result)
    }

    @Test
    fun `weekly review shows headline, wins, goal and the experiment`() {
        val monday = LocalDate.of(2026, 10, 12)
        val review = WeeklyReview.build(
            ReviewInput(
                weekStart = monday,
                days = (0..6).map { DayTotal(monday.plusDays(it.toLong()), (3 + it % 3) * h) },
                prevWeekMs = 40 * h,
                appsThis = mapOf("insta" to 10 * h),
                appsPrev = mapOf("insta" to 12 * h),
                ratings = emptyList(), checkIns = emptyList(), goalMin = 300,
                taper = TaperDecision(300, 275, 7, 7),
                weakSpots = listOf(WeakSpot(false, 22, "insta", 40 * min, 0.8f)),
                topNotif = null, trades = emptyList(), voice = Voice.Honest, label = { "Instagram" },
            ),
        )
        var tried: String? = null
        rule.setContent {
            GrompTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ReviewContent(review, isCurrentWeek = false, label = { "Instagram" }, canSetLimits = true) { tried = it.title }
                }
            }
        }
        rule.onNodeWithText(review.headline).assertIsDisplayed()
        rule.onNodeWithText(review.nextGoalLine!!).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Close your 10 pm window").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Set focus hours").performScrollTo().performClick()
        assertEquals("Close your 10 pm window", tried)
    }
}
