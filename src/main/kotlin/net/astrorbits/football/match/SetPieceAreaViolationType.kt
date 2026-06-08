package net.astrorbits.football.match

enum class SetPieceAreaViolationType {
    GK_HOLD_OUTSIDE_PENALTY_AREA,
    KICKOFF_CENTER_CIRCLE,
    KICKOFF_CROSS_MIDLINE,
    GOAL_KICK_OPPONENT_IN_AREA,
    CORNER_KICK_OPPONENT_IN_AREA,
    THROW_IN_OPPONENT_IN_AREA,
    PENALTY_KICK_INTRUSION,
    FREE_KICK_TOO_CLOSE,
    FREE_KICK_OPPONENT_IN_ATTACK_PA,
    GOAL_KICK_BALL_IN_AREA,
    FREE_KICK_BALL_IN_ATTACK_PA,
    ;

    /** 导致重发时的犯规说明（HUD）；无球员触发的重发不使用此字段。 */
    val restartReasonKey: String?
        get() = when (this) {
            KICKOFF_CENTER_CIRCLE -> "hud.nmbct-football.restart.reason.center_circle"
            KICKOFF_CROSS_MIDLINE -> "hud.nmbct-football.restart.reason.opponent_half"
            GOAL_KICK_OPPONENT_IN_AREA -> "hud.nmbct-football.restart.reason.penalty_area"
            CORNER_KICK_OPPONENT_IN_AREA -> "hud.nmbct-football.restart.reason.corner_area"
            THROW_IN_OPPONENT_IN_AREA -> "hud.nmbct-football.restart.reason.throw_in_area"
            PENALTY_KICK_INTRUSION -> "hud.nmbct-football.restart.reason.penalty_intrusion"
            FREE_KICK_TOO_CLOSE -> "hud.nmbct-football.restart.reason.free_kick_distance"
            FREE_KICK_OPPONENT_IN_ATTACK_PA -> "hud.nmbct-football.restart.reason.penalty_area"
            else -> null
        }

    val areaNameKey: String
        get() = when (this) {
            GK_HOLD_OUTSIDE_PENALTY_AREA -> "hud.nmbct-football.area.penalty_area"
            KICKOFF_CENTER_CIRCLE -> "hud.nmbct-football.area.center_circle"
            KICKOFF_CROSS_MIDLINE -> "hud.nmbct-football.area.opponent_half"
            GOAL_KICK_OPPONENT_IN_AREA -> "hud.nmbct-football.area.penalty_area"
            CORNER_KICK_OPPONENT_IN_AREA -> "hud.nmbct-football.area.corner_area"
            THROW_IN_OPPONENT_IN_AREA -> "hud.nmbct-football.area.throw_in_area"
            PENALTY_KICK_INTRUSION -> "hud.nmbct-football.area.penalty_arc_or_area"
            FREE_KICK_TOO_CLOSE -> "hud.nmbct-football.area.free_kick_distance"
            FREE_KICK_OPPONENT_IN_ATTACK_PA -> "hud.nmbct-football.area.penalty_area"
            GOAL_KICK_BALL_IN_AREA -> "hud.nmbct-football.area.penalty_area"
            FREE_KICK_BALL_IN_ATTACK_PA -> "hud.nmbct-football.area.penalty_area"
        }
}
