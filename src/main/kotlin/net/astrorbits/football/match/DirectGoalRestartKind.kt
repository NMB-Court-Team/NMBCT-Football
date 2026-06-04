package net.astrorbits.football.match

/** 掷界外球或半场开球后，须先切换进球归属球员，否则直接进门无效。 */
enum class DirectGoalRestartKind {
    THROW_IN,
    HALF_KICKOFF,
}
