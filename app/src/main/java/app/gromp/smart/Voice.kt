package app.gromp.smart

/** How Gromp talks to you. Chosen in Settings; Honest is the default. */
enum class Voice(val label: String, val blurb: String) {
    Gentle("Gentle", "Kind nudges, no judgement."),
    Honest("Honest", "Plain facts about where your time went."),
    Tough("Tough love", "Blunt, and the cost comes before you open the app.");

    /** Minutes in one pull app before a binge check-in. */
    val bingeAfterMin: Int get() = when (this) { Gentle -> 30; Honest -> 20; Tough -> 15 }

    /** Minutes until Gromp asks again after "keep going". */
    val bingeRepeatMin: Int get() = when (this) { Gentle -> 20; Honest -> 15; Tough -> 10 }

    companion object {
        fun from(name: String?): Voice = entries.firstOrNull { it.name == name } ?: Honest
    }
}

/** Things the user would rather spend time on. Each converts minutes into something concrete. */
enum class Trade(val label: String, val minutesPerUnit: Int, val one: String, val many: String) {
    Reading("Reading", 20, "chapter of a book", "chapters of a book"),
    Exercise("Exercise", 45, "workout", "workouts"),
    Sleep("Sleep", 60, "hour of sleep", "hours of sleep"),
    People("Family & friends", 60, "hour with people you love", "hours with people you love"),
    Walking("Walks outside", 30, "30-minute walk", "30-minute walks"),
    Learning("Learning a skill", 30, "lesson", "lessons"),
    Project("Side project", 60, "hour on your side project", "hours on your side project"),
    Films("Films", 120, "film", "films");

    companion object {
        val Defaults = listOf(Reading, Walking, Sleep)

        fun parse(names: Collection<String>): List<Trade> = names.mapNotNull { n -> entries.firstOrNull { it.name == n } }
    }
}

/** Why the user opened an app. Drift check-ins fire if they're still there long after. */
enum class IntentKind(val label: String, val driftMin: Int) {
    Reply("Reply to someone", 10),
    LookUp("Look something up", 10),
    Post("Post something", 10),
    Bored("Just bored", 15);

    companion object {
        fun from(name: String?): IntentKind? = entries.firstOrNull { it.name == name }
    }
}
