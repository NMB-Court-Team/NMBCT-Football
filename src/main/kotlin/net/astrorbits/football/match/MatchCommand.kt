package net.astrorbits.football.match

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component

object MatchCommand {
	fun register(dispatcher: com.mojang.brigadier.CommandDispatcher<CommandSourceStack>) {
		val root = Commands.literal("match")

		root.then(Commands.literal("start").executes {
			MatchState.isRunning = true
			it.source.sendSuccess({ Component.literal("计时已开始") }, true)
			1
		})

		root.then(Commands.literal("pause").executes {
			MatchState.isRunning = false
			it.source.sendSuccess({ Component.literal("计时已暂停") }, true)
			1
		})

		root.then(Commands.literal("reset").executes {
			MatchState.reset()
			it.source.sendSuccess({ Component.literal("比赛已重置") }, true)
			1
		})

		root.then(Commands.literal("scoreA").then(
			Commands.argument("value", IntegerArgumentType.integer(0))
				.executes {
					MatchState.teamAScore = IntegerArgumentType.getInteger(it, "value")
					it.source.sendSuccess({ Component.literal("队伍A得分: ${MatchState.teamAScore}") }, true)
					1
				}
		))

		root.then(Commands.literal("scoreB").then(
			Commands.argument("value", IntegerArgumentType.integer(0))
				.executes {
					MatchState.teamBScore = IntegerArgumentType.getInteger(it, "value")
					it.source.sendSuccess({ Component.literal("队伍B得分: ${MatchState.teamBScore}") }, true)
					1
				}
		))

		root.then(Commands.literal("nameA").then(
			Commands.argument("name", StringArgumentType.string())
				.executes {
					MatchState.teamAName = StringArgumentType.getString(it, "name")
					it.source.sendSuccess({ Component.literal("队伍A名称: ${MatchState.teamAName}") }, true)
					1
				}
		))

		root.then(Commands.literal("nameB").then(
			Commands.argument("name", StringArgumentType.string())
				.executes {
					MatchState.teamBName = StringArgumentType.getString(it, "name")
					it.source.sendSuccess({ Component.literal("队伍B名称: ${MatchState.teamBName}") }, true)
					1
				}
		))

		registerGoalkeeperCommands(root)

		dispatcher.register(root)
	}

	private fun registerGoalkeeperCommands(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>) {
		val setGk = Commands.literal("setGk")
		for (team in TeamSide.entries) {
			val teamLabel = team.name
			setGk.then(
				Commands.literal(teamLabel).then(
					Commands.argument("player", EntityArgument.player())
						.executes { ctx ->
							val player = EntityArgument.getPlayer(ctx, "player")
							PlayerRoleState.setOfficialGk(team, player)
							ctx.source.sendSuccess({
								Component.literal("已将 ${player.gameProfile.name} 设为${teamLabel}队守门员")
							}, true)
							1
						}
				)
			)
		}
		root.then(setGk)

		val clearGk = Commands.literal("clearGk")
		for (team in TeamSide.entries) {
			val teamLabel = team.name
			clearGk.then(
				Commands.literal(teamLabel).executes { ctx ->
					PlayerRoleState.clearOfficialGk(team, ctx.source.server)
					ctx.source.sendSuccess({ Component.literal("已清除${teamLabel}队守门员") }, true)
					1
				}
			)
		}
		root.then(clearGk)

		root.then(
			Commands.literal("gk").then(
				Commands.literal("on").executes { ctx ->
					val player = ctx.source.playerOrException
					PlayerRoleState.setVoluntaryGk(player, true)
					ctx.source.sendSuccess({ Component.literal("已开启守门员模式") }, true)
					1
				}
			).then(
				Commands.literal("off").executes { ctx ->
					val player = ctx.source.playerOrException
					PlayerRoleState.setVoluntaryGk(player, false)
					ctx.source.sendSuccess({ Component.literal("已关闭守门员模式") }, true)
					1
				}
			)
		)
	}
}

