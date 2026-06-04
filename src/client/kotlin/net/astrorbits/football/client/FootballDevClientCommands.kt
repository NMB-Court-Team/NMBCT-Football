package net.astrorbits.football.client

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.Component

/** 仅开发环境注册的客户端调试命令。 */
object FootballDevClientCommands {
    fun register() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment) {
            return
        }
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("footballdev")
                    .then(
                        ClientCommands.literal("ballIndicator")
                            .executes { ctx ->
                                val enabled = DribbleBallIndicatorClient.toggleDevAlwaysShowNearestFootballIndicator()
                                val key = if (enabled) {
                                    "command.nmbct-football.dev.ball_indicator.on"
                                } else {
                                    "command.nmbct-football.dev.ball_indicator.off"
                                }
                                ctx.source.sendFeedback(Component.translatable(key))
                                1
                            },
                    ),
            )
        }
    }
}
