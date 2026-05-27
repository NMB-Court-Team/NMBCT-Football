package net.astrorbits.football

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.context.CommandContext
import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.util.FootballKickUtil
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3

object FootballCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val command = Commands.literal("football").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
        command.then(Commands.literal("summon")
            .executes { context ->
                val source = context.source
                val level = source.level
                val pos = source.position
                val football = Football(Football.ENTITY_TYPE, level)
                football.setPos(pos.x, pos.y, pos.z)
                level.addFreshEntity(football)
                source.sendSuccess({ Component.literal("Summoned football") }, true)
                1
            }
        ).then(Commands.literal("kick")
            .then(Commands.argument("force", DoubleArgumentType.doubleArg(0.0, 10.0))
                .executes { context -> executeKick(context, heightOffset = 0.0, angleDegrees = 0.0) }
                .then(Commands.argument("height", DoubleArgumentType.doubleArg(-0.5, 1.0))
                    .executes { context ->
                        executeKick(context, DoubleArgumentType.getDouble(context, "height"), angleDegrees = 0.0)
                    }
                    .then(Commands.argument("angle", DoubleArgumentType.doubleArg(-90.0, 90.0))
                        .executes { context ->
                            executeKick(
                                context,
                                DoubleArgumentType.getDouble(context, "height"),
                                DoubleArgumentType.getDouble(context, "angle")
                            )
                        }
                    )
                )
            )
        )

        dispatcher.register(command)
    }

    private fun executeKick(
        context: CommandContext<CommandSourceStack>,
        heightOffset: Double,
        angleDegrees: Double
    ): Int {
        val source = context.source
        val player = source.player
        if (player == null) {
            source.sendFailure(Component.literal("Only players can kick footballs"))
            return 0
        }

        val force = DoubleArgumentType.getDouble(context, "force")
        val football = FootballKickUtil.findNearestFootball(player, FootballInputConfig.COMMAND_KICK_RANGE)
        if (football == null) {
            source.sendFailure(Component.literal("No football nearby"))
            return 0
        }

        val params = net.astrorbits.football.util.KickParams(
            force = force,
            angleDegrees = angleDegrees,
            heightOffset = heightOffset
        )
        FootballKickUtil.applyKickToFootball(player, football, params)
        source.sendSuccess({
            Component.literal("Kicked football (force=$force, height=$heightOffset, angle=${angleDegrees}°)")
        }, true)
        return 1
    }
}
