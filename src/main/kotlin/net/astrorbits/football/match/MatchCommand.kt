package net.astrorbits.football.match

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.Commands

object MatchCommand {
	fun register(dispatcher: com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>) {
		val root = Commands.literal("match")

		root.then(Commands.literal("start").executes {
			MatchState.isRunning = true
			it.source.sendSuccess({ net.minecraft.network.chat.Component.literal("计时已开始") }, true)
			1
		})

		root.then(Commands.literal("pause").executes {
			MatchState.isRunning = false
			it.source.sendSuccess({ net.minecraft.network.chat.Component.literal("计时已暂停") }, true)
			1
		})

		root.then(Commands.literal("reset").executes {
			MatchState.reset()
			it.source.sendSuccess({ net.minecraft.network.chat.Component.literal("比赛已重置") }, true)
			1
		})

		root.then(Commands.literal("scoreA").then(
			Commands.argument("value", IntegerArgumentType.integer(0))
				.executes {
					MatchState.teamAScore = IntegerArgumentType.getInteger(it, "value")
					it.source.sendSuccess({ net.minecraft.network.chat.Component.literal("队伍A得分: ${MatchState.teamAScore}") }, true)
					1
				}
		))

		root.then(Commands.literal("scoreB").then(
			Commands.argument("value", IntegerArgumentType.integer(0))
				.executes {
					MatchState.teamBScore = IntegerArgumentType.getInteger(it, "value")
					it.source.sendSuccess({ net.minecraft.network.chat.Component.literal("队伍B得分: ${MatchState.teamBScore}") }, true)
					1
				}
		))

		root.then(Commands.literal("nameA").then(
			Commands.argument("name", StringArgumentType.string())
				.executes {
					MatchState.teamAName = StringArgumentType.getString(it, "name")
					it.source.sendSuccess({ net.minecraft.network.chat.Component.literal("队伍A名称: ${MatchState.teamAName}") }, true)
					1
				}
		))

		root.then(Commands.literal("nameB").then(
			Commands.argument("name", StringArgumentType.string())
				.executes {
					MatchState.teamBName = StringArgumentType.getString(it, "name")
					it.source.sendSuccess({ net.minecraft.network.chat.Component.literal("队伍B名称: ${MatchState.teamBName}") }, true)
					1
				}
		))

		dispatcher.register(root)
	}
}
