package net.astrorbits.football

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.context.CommandContext
import kotlin.math.cos
import kotlin.math.sin
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.util.Vec3Math
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
        val look = player.lookAngle
        val horizontalLook = Vec3Math.horizontal(look)
        val direction = buildKickDirection(horizontalLook, look, force, angleDegrees)

        val football = player.level().getEntitiesOfClass(
            Football::class.java,
            player.boundingBox.inflate(3.0)
        ).minByOrNull { it.distanceToSqr(player) }

        if (football == null) {
            source.sendFailure(Component.literal("No football nearby"))
            return 0
        }

        val ballCenter = football.position().add(0.0, FootballPhysicsConfig.RADIUS, 0.0)
        val kickPoint = buildKickPoint(ballCenter, horizontalLook, heightOffset)

        football.kick(kickPoint, direction)
        source.sendSuccess({
            Component.literal("Kicked football (force=$force, height=$heightOffset, angle=${angleDegrees}°)")
        }, true)
        return 1
    }

    /**
     * @param angleDegrees 相对水平面的仰角（度）；0° 为水平，正值为上挑，负值为下压。
     */
    private fun buildKickDirection(
        horizontalLook: Vec3,
        look: Vec3,
        force: Double,
        angleDegrees: Double
    ): Vec3 {
        if (horizontalLook.lengthSqr() < 1.0e-8) {
            return look.scale(force)
        }

        val horizontalUnit = Vec3Math.normalizeSafe(horizontalLook)
        val pitchRad = Math.toRadians(angleDegrees)
        val unitDirection = horizontalUnit.scale(cos(pitchRad)).add(0.0, sin(pitchRad), 0.0)
        return unitDirection.scale(force)
    }

    /**
     * @param heightOffset 相对球心的竖直偏移（格）；0 为赤道高度，负值为偏下，正值为偏上。
     */
    private fun buildKickPoint(ballCenter: Vec3, horizontalLook: Vec3, heightOffset: Double): Vec3 {
        val horizontalOffset = if (horizontalLook.lengthSqr() > 1.0e-8) {
            Vec3Math.normalizeSafe(horizontalLook).scale(-FootballPhysicsConfig.RADIUS)
        } else {
            Vec3.ZERO
        }
        return ballCenter.add(horizontalOffset.x, heightOffset, horizontalOffset.z)
    }
}
