package net.astrorbits.football

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.astrorbits.football.config.FootballConfigs
import net.astrorbits.football.config.server.FootballServerConfigHolder
import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.util.FootballKickUtil
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.network.chat.Component

object FootballCommand {
    private val NOT_A_FOOTBALL = SimpleCommandExceptionType(
        Component.translatable("command.nmbct-football.kick.not_a_football")
    )
    private val ZERO_DIRECTION = SimpleCommandExceptionType(
        Component.translatable("command.nmbct-football.kick.zero_direction")
    )

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
                source.sendSuccess({ Component.translatable("command.nmbct-football.summon") }, true)
                1
            }
        ).then(Commands.literal("config")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .executes { context ->
                val player = context.source.player
                if (player == null) {
                    context.source.sendFailure(Component.translatable("command.nmbct-football.config.player_only"))
                    return@executes 0
                }
                FootballNetworking.sendServerConfigSync(player, FootballServerConfigHolder.current)
                context.source.sendSuccess(
                    { Component.translatable("command.nmbct-football.config.opened") },
                    true,
                )
                1
            }
        ).then(registerKickCommand())

        dispatcher.register(command)
    }

    private fun registerKickCommand(): LiteralArgumentBuilder<CommandSourceStack> {
        val simple = registerSimpleKickBranch()
        val precise = registerPreciseKickBranch()

        val kick = Commands.literal("kick")
        kick.then(Commands.literal("help").executes { executeKickHelp(it) })
        kick.then(Commands.literal("entity")
            .then(Commands.argument("target", EntityArgument.entity())
                .then(simple)
                .then(precise)
            )
        )
        kick.then(simple)
        kick.then(precise)
        return kick
    }

    private fun registerSimpleKickBranch(): LiteralArgumentBuilder<CommandSourceStack> {
        val powerArg = Commands.argument("power", DoubleArgumentType.doubleArg(0.0, 10.0))
            .executes { context ->
                executeSimpleKick(context, DoubleArgumentType.getDouble(context, "power"), 0.0)
            }
            .then(Commands.literal("elevation")
                .then(Commands.argument("elevation", DoubleArgumentType.doubleArg(-90.0, 90.0))
                    .executes { context ->
                        executeSimpleKick(
                            context,
                            DoubleArgumentType.getDouble(context, "power"),
                            DoubleArgumentType.getDouble(context, "elevation"),
                        )
                    }
                )
            )

        return Commands.literal("simple")
            .executes { executeSimpleKick(it, 1.0, 0.0) }
            .then(powerArg)
    }

    private fun registerPreciseKickBranch(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("precise")
            .then(Commands.literal("at")
                .then(Commands.argument("at", Vec3Argument.vec3())
                    .then(Commands.literal("toward")
                        .then(Commands.argument("toward", Vec3Argument.vec3())
                            .then(Commands.literal("power")
                                .then(Commands.argument("power", DoubleArgumentType.doubleArg(0.0, 10.0))
                                    .executes { context ->
                                        executePreciseKick(
                                            context,
                                            Vec3Argument.getVec3(context, "at"),
                                            Vec3Argument.getVec3(context, "toward"),
                                            DoubleArgumentType.getDouble(context, "power"),
                                        )
                                    }
                                )
                            )
                        )
                    )
                )
            )
    }

    private fun executeKickHelp(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        fun send(component: Component) {
            source.sendSuccess({ component }, false)
        }

        send(
            Component.translatable("command.nmbct-football.kick.help.header")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
        )
        send(Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.DARK_GRAY))

        send(helpSection("command.nmbct-football.kick.help.target.title"))
        send(helpBullet("command.nmbct-football.kick.help.target.nearby"))
        send(helpSyntax("/football kick entity <target> ..."))
        send(helpIndented("command.nmbct-football.kick.help.target.entity"))

        send(Component.empty())

        send(helpSection("command.nmbct-football.kick.help.simple.title"))
        send(helpSyntax("/football kick [entity <target>] simple [<power>] [elevation <elevation>]"))
        send(helpParam("power", "command.nmbct-football.kick.help.simple.power"))
        send(helpParam("elevation", "command.nmbct-football.kick.help.simple.elevation"))
        send(helpNote("command.nmbct-football.kick.help.simple.note"))

        send(Component.empty())

        send(helpSection("command.nmbct-football.kick.help.precise.title"))
        send(helpSyntax("/football kick [entity <target>] precise at <x> <y> <z> toward <x> <y> <z> power <power>"))
        send(helpParam("at", "command.nmbct-football.kick.help.precise.at"))
        send(helpParam("toward", "command.nmbct-football.kick.help.precise.toward"))
        send(helpParam("power", "command.nmbct-football.kick.help.precise.power"))
        send(helpNote("command.nmbct-football.kick.help.precise.note"))

        send(Component.empty())

        send(helpSection("command.nmbct-football.kick.help.examples.title"))
        send(helpExample("execute rotated 90 0 run football kick simple power 2 elevation 20"))
        send(helpExample("execute at @n[type=nmbct-football:football] run football kick entity @s precise at ~ ~-0.25 ~ toward ~ ~5 ~ power 2"))

        return 1
    }

    private fun helpSection(key: String): Component =
        Component.literal("▸ ")
            .withStyle(ChatFormatting.YELLOW)
            .append(Component.translatable(key).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))

    private fun helpBullet(key: String): Component =
        Component.literal("  • ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.translatable(key).withStyle(ChatFormatting.WHITE))

    private fun helpSyntax(syntax: String): Component =
        Component.literal("  ")
            .append(Component.literal(syntax).withStyle(ChatFormatting.GREEN))

    private fun helpIndented(key: String): Component =
        Component.literal("    ")
            .append(Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))

    private fun helpParam(name: String, descKey: String): Component =
        Component.literal("    ")
            .append(Component.literal(name).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
            .append(Component.literal(" — ").withStyle(ChatFormatting.GRAY))
            .append(Component.translatable(descKey).withStyle(ChatFormatting.WHITE))

    private fun helpNote(key: String): Component =
        Component.literal("    » ")
            .withStyle(ChatFormatting.AQUA)
            .append(Component.translatable(key).withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC))

    private fun helpExample(command: String): Component =
        Component.literal("  › ")
            .withStyle(ChatFormatting.GOLD)
            .append(Component.literal(command).withStyle(ChatFormatting.LIGHT_PURPLE))

    private fun executeSimpleKick(
        context: CommandContext<CommandSourceStack>,
        power: Double,
        elevation: Double,
    ): Int {
        val source = context.source
        val football = resolveFootball(context) ?: return 0
        val rotation = source.rotation
        FootballKickUtil.applySimpleCommandKick(football, power, elevation, rotation.y, rotation.x)
        source.sendSuccess({
            Component.translatable(
                "command.nmbct-football.kick.simple.success",
                football.displayName,
                power,
                elevation,
            )
        }, true)
        return 1
    }

    private fun executePreciseKick(
        context: CommandContext<CommandSourceStack>,
        kickPoint: net.minecraft.world.phys.Vec3,
        towardPoint: net.minecraft.world.phys.Vec3,
        power: Double,
    ): Int {
        val source = context.source
        val football = resolveFootball(context) ?: return 0
        val direction = FootballKickUtil.buildPreciseKickDirection(kickPoint, towardPoint, power)
            ?: throw ZERO_DIRECTION.create()
        FootballKickUtil.applyPreciseCommandKick(football, kickPoint, direction)
        source.sendSuccess({
            Component.translatable(
                "command.nmbct-football.kick.precise.success",
                football.displayName,
                formatVec3(kickPoint),
                formatVec3(towardPoint),
                power,
            )
        }, true)
        return 1
    }

    private fun resolveFootball(context: CommandContext<CommandSourceStack>): Football? {
        val source = context.source
        val entity = getOptionalEntityTarget(context)
        if (entity != null) {
            if (entity !is Football) {
                throw NOT_A_FOOTBALL.create()
            }
            return entity
        }

        val kickRange = FootballConfigs.server.playerInput.commandKickRange
        val football = FootballKickUtil.findNearestFootball(source.level, source.position, kickRange)
        if (football == null) {
            source.sendFailure(Component.translatable("command.nmbct-football.kick.no_football_nearby"))
        }
        return football
    }

    private fun getOptionalEntityTarget(context: CommandContext<CommandSourceStack>): net.minecraft.world.entity.Entity? {
        return try {
            EntityArgument.getEntity(context, "target")
        } catch (_: Exception) {
            null
        }
    }

    private fun formatVec3(vec: net.minecraft.world.phys.Vec3): String =
        String.format("%.2f, %.2f, %.2f", vec.x, vec.y, vec.z)
}
