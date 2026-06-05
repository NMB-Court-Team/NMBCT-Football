package net.astrorbits.football.match

enum class SetPieceAreaViolationType {
    GK_HOLD_OUTSIDE_PENALTY_AREA,
    KICKOFF_CENTER_CIRCLE,
    KICKOFF_CROSS_MIDLINE,
    GOAL_KICK_OPPONENT_IN_AREA,
    CORNER_KICK_OPPONENT_IN_AREA,
    THROW_IN_OPPONENT_IN_AREA,
    PENALTY_KICK_INTRUSION,
    ;

    val areaNameKey: String
        get() = when (this) {
            GK_HOLD_OUTSIDE_PENALTY_AREA -> "hud.nmbct-football.area.penalty_area"
            KICKOFF_CENTER_CIRCLE -> "hud.nmbct-football.area.center_circle"
            KICKOFF_CROSS_MIDLINE -> "hud.nmbct-football.area.opponent_half"
            GOAL_KICK_OPPONENT_IN_AREA -> "hud.nmbct-football.area.penalty_area"
            CORNER_KICK_OPPONENT_IN_AREA -> "hud.nmbct-football.area.corner_area"
            THROW_IN_OPPONENT_IN_AREA -> "hud.nmbct-football.area.throw_in_area"
            PENALTY_KICK_INTRUSION -> "hud.nmbct-football.area.penalty_arc_or_area"
        }
}
