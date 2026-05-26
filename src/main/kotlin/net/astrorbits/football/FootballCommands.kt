package net.astrorbits.football

import com.mojang.brigadier.arguments.DoubleArgumentType
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3

object FootballCommands {
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("football")
                    .then(
                        Commands.literal("summon")
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
                    )
                    .then(
                        Commands.literal("kick")
                            .then(
                                Commands.argument("force", DoubleArgumentType.doubleArg(0.0, 10.0))
                                    .executes { context ->
                                        val source = context.source
                                        val player = source.player
                                        if (player == null) {
                                            source.sendFailure(Component.literal("Only players can kick footballs"))
                                            return@executes 0
                                        }

                                        val force = DoubleArgumentType.getDouble(context, "force")
                                        val look = player.lookAngle
                                        val direction = look.scale(force)
                                        val kickPoint = player.eyePosition.add(look.scale(0.5))

                                        val football = player.level().getEntitiesOfClass(
                                            Football::class.java,
                                            player.boundingBox.inflate(3.0)
                                        ).minByOrNull { it.distanceToSqr(player) }

                                        if (football == null) {
                                            source.sendFailure(Component.literal("No football nearby"))
                                            return@executes 0
                                        }

                                        football.kick(kickPoint, direction)
                                        source.sendSuccess({ Component.literal("Kicked football with force $force") }, true)
                                        1
                                    }
                            )
                    )
            )
        }
    }
}
