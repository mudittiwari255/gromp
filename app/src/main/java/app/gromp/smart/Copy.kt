package app.gromp.smart

import app.gromp.util.Fmt

/** Words for each check-in, in each voice. Kept together so tone stays consistent. */
object Copy {

    data class CheckIn(val title: String, val body: String, val leave: String, val stay: String)

    fun intention(app: String, voice: Voice): CheckIn = when (voice) {
        Voice.Gentle -> CheckIn("What brings you to $app?", "A quick answer helps you notice when you've drifted.", "Never mind, close it", "Open $app")
        Voice.Honest -> CheckIn("What are you opening $app for?", "Gromp will check in if you're still here long after.", "Nothing, close it", "Open $app")
        Voice.Tough -> CheckIn("Why $app, right now?", "Be honest. Gromp will hold you to it.", "No good reason, close it", "Open $app")
    }

    fun drift(app: String, kind: IntentKind, minutes: Int, voice: Voice): CheckIn {
        val reason = when (kind) {
            IntentKind.Reply -> "to reply to someone"
            IntentKind.LookUp -> "to look something up"
            IntentKind.Post -> "to post something"
            IntentKind.Bored -> "because you were bored"
        }
        return when (voice) {
            Voice.Gentle -> CheckIn("Still in $app?", "You came here $reason $minutes minutes ago. Did you get what you came for?", "Yes, I'm done", "Not yet")
            Voice.Honest -> CheckIn("$minutes minutes in $app", "You opened it $reason. Is that still what you're doing?", "Done, close it", "Still on it")
            Voice.Tough -> CheckIn("You said $reason.", "That was $minutes minutes ago. This isn't that any more.", "Fair, close it", "Leave me alone for 10 min")
        }
    }

    fun binge(app: String, minutes: Int, costPhrase: String?, voice: Voice): CheckIn {
        val cost = costPhrase?.let { " That's $it." } ?: ""
        return when (voice) {
            Voice.Gentle -> CheckIn("$minutes minutes in $app", "Just checking in.$cost Want to take a break?", "Take a break", "Keep going")
            Voice.Honest -> CheckIn("$minutes minutes straight in $app", "Still choosing this?$cost", "Close it", "Keep going")
            Voice.Tough -> CheckIn("$minutes minutes. Gone.", "${costPhrase?.let { "You just spent $it here." } ?: "That time isn't coming back."} Is this really what you want?", "Close it now", "Keep wasting it")
        }
    }

    fun loop(apps: Int, spanMs: Long, voice: Voice): CheckIn {
        val min = maxOf(1, spanMs / 60_000)
        return when (voice) {
            Voice.Gentle -> CheckIn("Looking for something?", "You've moved between $apps apps in $min minutes. Maybe there's something else you need.", "Put the phone down", "I'm fine")
            Voice.Honest -> CheckIn("$apps apps in $min minutes", "That's the scroll loop. Nothing new is going to show up.", "Break the loop", "Keep going")
            Voice.Tough -> CheckIn("You're looping.", "$apps apps in $min minutes, hunting for a hit that isn't there.", "Stop, close it", "Keep looping")
        }
    }

    /** Sentence for the "worth it?" notification. */
    fun worthPrompt(app: String, ms: Long, voice: Voice): Pair<String, String> {
        val t = Fmt.duration(ms)
        return when (voice) {
            Voice.Gentle -> "How did that $t on $app feel?" to "One tap helps Gromp learn what's worth your time."
            Voice.Honest -> "$t on $app. Worth it?" to "Your answers decide where limits go."
            Voice.Tough -> "$t on $app. Be honest: worth it?" to "No one else sees this but you."
        }
    }

    fun headsUp(spot: WeakSpot, app: String, voice: Voice): Pair<String, String> {
        val avg = Fmt.duration(spot.avgMs)
        val whenText = WeakSpots.describe(spot).lowercase()
        return when (voice) {
            Voice.Gentle -> "A heads-up before ${spot.hour.toClock()}" to "${whenText.replaceFirstChar { it.uppercase() }} usually turn into $avg of $app. Want to plan something else?"
            Voice.Honest -> "Your ${spot.hour.toClock()} slump is coming" to "${whenText.replaceFirstChar { it.uppercase() }} you average $avg on $app. Focus hours can close it for tonight."
            Voice.Tough -> "${spot.hour.toClock()}. You know what happens next." to "$avg of $app, most ${if (spot.weekend) "weekends" else "weekdays"}. Decide now, not at ${spot.hour.toClock()}."
        }
    }

    private fun Int.toClock(): String {
        val h = this % 12
        return "${if (h == 0) 12 else h} ${if (this < 12) "am" else "pm"}"
    }
}
