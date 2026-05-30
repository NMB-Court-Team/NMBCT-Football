package net.astrorbits.football.match

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.ComponentArgument
import net.minecraft.commands.arguments.EntityArgument
import net.astrorbits.football.network.FootballNetworking
import net.minecraft.network.chat.Component

object MatchCommand {
	fun register(dispatcher: CommandDispatcher<CommandSourceStack>, context: CommandBuildContext) {
		val root = Commands.literal("match")

		root.then(Commands.literal("start").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes {
			PlayerRoleState.randomAssignGoalkeepers(it.source.server)
			MatchState.teleportTeamsToSpawnPositions(it.source.server)
			MatchState.advancePhase()
			1
		})

		root.then(Commands.literal("pause").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes {
			MatchState.isRunning = !MatchState.isRunning
			val key = if (MatchState.isRunning) "command.nmbct-football.match.timer_started" else "command.nmbct-football.match.timer_paused"
			it.source.sendSuccess({ Component.translatable(key) }, true)
			1
		})

		registerPhaseCommands(root)

		root.then(Commands.literal("reset").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes {
			MatchState.clearScoreboardTeams(it.source.server)
			MatchState.reset()
			it.source.sendSuccess({ Component.translatable("command.nmbct-football.match.reset") }, true)
			1
		})

		root.then(Commands.literal("scoreA").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(
			Commands.argument("value", IntegerArgumentType.integer(0))
				.executes {
					MatchState.teamAScore = IntegerArgumentType.getInteger(it, "value")
					it.source.sendSuccess({
						Component.translatable("command.nmbct-football.match.score_a", MatchState.teamAScore)
					}, true)
					1
				}
		))

		root.then(Commands.literal("scoreB").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(
			Commands.argument("value", IntegerArgumentType.integer(0))
				.executes {
					MatchState.teamBScore = IntegerArgumentType.getInteger(it, "value")
					it.source.sendSuccess({
						Component.translatable("command.nmbct-football.match.score_b", MatchState.teamBScore)
					}, true)
					1
				}
		))

		root.then(Commands.literal("nameA").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(
			Commands.argument("name", ComponentArgument.textComponent(context))
				.executes {
					MatchState.teamAName = ComponentArgument.getResolvedComponent(it, "name")
					it.source.sendSuccess({
						Component.translatable("command.nmbct-football.match.name_a", MatchState.getTeamName(TeamSide.A))
					}, true)
					1
				}
		))

		root.then(Commands.literal("nameB").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(
			Commands.argument("name", ComponentArgument.textComponent(context))
				.executes {
					MatchState.teamBName = ComponentArgument.getResolvedComponent(it, "name")
					it.source.sendSuccess({
						Component.translatable("command.nmbct-football.match.name_b", MatchState.getTeamName(TeamSide.B))
					}, true)
					1
				}
		))

		root.then(Commands.literal("setup")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.executes { ctx ->
				val player = ctx.source.player
				if (player == null) {
					ctx.source.sendFailure(Component.translatable("command.nmbct-football.config.player_only"))
					return@executes 0
				}
				FootballNetworking.sendMatchConfigSync(player, MatchConfigHolder.current)
				ctx.source.sendSuccess(
					{ Component.translatable("command.nmbct-football.match.setup_opened") },
					true,
				)
				1
			}
		)

		root.then(Commands.literal("setting")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.executes { ctx ->
				val player = ctx.source.player
				if (player == null) {
					ctx.source.sendFailure(Component.translatable("command.nmbct-football.config.player_only"))
					return@executes 0
				}
				FootballNetworking.sendMatchFieldConfigSync(player, MatchConfigHolder.current)
				ctx.source.sendSuccess(
					{ Component.translatable("command.nmbct-football.match.setting_opened") },
					true,
				)
				1
			}
		)

		registerTeamCommands(root)
		registerGoalkeeperCommands(root)

		dispatcher.register(root)
	}

	private fun registerPhaseCommands(root: LiteralArgumentBuilder<CommandSourceStack>) {
		val phaseCmd = Commands.literal("phase").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))

		// /match phase — show current phase
		phaseCmd.executes { ctx ->
			val phase = MatchState.currentPhase
			val timeInfo = MatchState.formatElapsed(MatchState.getPhaseDisplayTicks())
			ctx.source.sendSuccess(
				{
					Component.translatable(
						"command.nmbct-football.match.phase.current",
						Component.translatable(phase.displayNameKey),
						timeInfo,
					)
				},
				true,
			)
			1
		}

		// /match phase advance — advance to next phase (no chat message)
		phaseCmd.then(Commands.literal("advance").executes {
			MatchState.advancePhase()
			1
		})

		// /match phase set <phase_name> — set to specific phase
		val setCmd = Commands.literal("set")
		for (phase in MatchPhase.entries) {
			setCmd.then(Commands.literal(phase.name).executes { ctx ->
				MatchState.setPhase(phase)
				ctx.source.sendSuccess(
					{
						Component.translatable(
							"command.nmbct-football.match.phase.set",
							Component.translatable(phase.displayNameKey),
						)
					},
					true,
				)
				1
			})
		}
		phaseCmd.then(setCmd)

		root.then(phaseCmd)
	}

	private fun registerTeamCommands(root: LiteralArgumentBuilder<CommandSourceStack>) {
		val joinCmd = Commands.literal("join")
		for (team in TeamSide.entries) {
			joinCmd.then(
				Commands.literal(team.name).executes { ctx ->
					val player = ctx.source.playerOrException
					MatchState.removePlayer(player.uuid)
					MatchState.addPlayer(team, player.uuid)
					MatchState.syncPlayerScoreboard(player.uuid, team, ctx.source.server)
					ctx.source.sendSuccess({
						Component.translatable("command.nmbct-football.match.join", MatchState.getTeamName(team))
					}, true)
					1
				}
			)
		}
		root.then(joinCmd)

		root.then(Commands.literal("leave").executes { ctx ->
			val player = ctx.source.playerOrException
			if (MatchState.removePlayer(player.uuid)) {
				MatchState.syncPlayerScoreboard(player.uuid, null, ctx.source.server)
				ctx.source.sendSuccess({ Component.translatable("command.nmbct-football.match.leave") }, true)
			} else {
				ctx.source.sendSuccess({ Component.translatable("command.nmbct-football.match.leave_not_in_team") }, true)
			}
			1
		})

		val clearCmd = Commands.literal("clear").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.executes { ctx ->
				MatchState.clearScoreboardTeams(ctx.source.server)
				MatchState.teamAPlayers.clear()
				MatchState.teamBPlayers.clear()
				ctx.source.sendSuccess({ Component.translatable("command.nmbct-football.match.clear_all") }, true)
				1
			}
		for (team in TeamSide.entries) {
			clearCmd.then(
				Commands.literal(team.name).executes { ctx ->
					val players = when (team) {
						TeamSide.A -> MatchState.teamAPlayers.toList()
						TeamSide.B -> MatchState.teamBPlayers.toList()
					}
					for (uuid in players) {
						MatchState.syncPlayerScoreboard(uuid, null, ctx.source.server)
					}
					when (team) {
						TeamSide.A -> MatchState.teamAPlayers.clear()
						TeamSide.B -> MatchState.teamBPlayers.clear()
					}
					ctx.source.sendSuccess({
						Component.translatable("command.nmbct-football.match.clear_team", MatchState.getTeamName(team))
					}, true)
					1
				}
			)
		}
		root.then(clearCmd)
	}

	private fun registerGoalkeeperCommands(root: LiteralArgumentBuilder<CommandSourceStack>) {
		val setGk = Commands.literal("setGk").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
		for (team in TeamSide.entries) {
			val teamLabel = team.name
			setGk.then(
				Commands.literal(teamLabel).then(
					Commands.argument("player", EntityArgument.player())
						.executes { ctx ->
							val player = EntityArgument.getPlayer(ctx, "player")
							PlayerRoleState.setOfficialGk(team, player)
							ctx.source.sendSuccess({
								Component.translatable(
									"command.nmbct-football.match.set_gk",
									player.gameProfile.name,
									MatchState.getTeamName(team),
								)
							}, true)
							1
						}
				)
			)
		}
		root.then(setGk)

		val clearGk = Commands.literal("clearGk").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
		for (team in TeamSide.entries) {
			val teamLabel = team.name
			clearGk.then(
				Commands.literal(teamLabel).executes { ctx ->
					PlayerRoleState.clearOfficialGk(team, ctx.source.server)
					ctx.source.sendSuccess({
						Component.translatable("command.nmbct-football.match.clear_gk", MatchState.getTeamName(team))
					}, true)
					1
				}
			)
		}
		root.then(clearGk)

		root.then(
			Commands.literal("gk").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(
				Commands.literal("on").executes { ctx ->
					val player = ctx.source.playerOrException
					PlayerRoleState.setVoluntaryGk(player, true)
					ctx.source.sendSuccess({ Component.translatable("command.nmbct-football.match.gk_on") }, true)
					1
				}
			).then(
				Commands.literal("off").executes { ctx ->
					val player = ctx.source.playerOrException
					PlayerRoleState.setVoluntaryGk(player, false)
					ctx.source.sendSuccess({ Component.translatable("command.nmbct-football.match.gk_off") }, true)
					1
				}
			)
		)
	}
}
