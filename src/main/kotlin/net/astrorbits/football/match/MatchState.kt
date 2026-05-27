package net.astrorbits.football.match

object MatchState {
	var timerTicks = 0
	var isRunning = true
	var teamAName = "红队"
	var teamBName = "蓝队"
	var teamAScore = 0
	var teamBScore = 0

	fun reset() {
		timerTicks = 0
		isRunning = true
		teamAScore = 0
		teamBScore = 0
		PlayerRoleState.reset()
	}

	fun togglePause() {
		isRunning = !isRunning
	}

	fun formatTime(): String {
		val totalSeconds = timerTicks / 20
		val minutes = totalSeconds / 60
		val seconds = totalSeconds % 60
		return "%02d:%02d".format(minutes, seconds)
	}
}
