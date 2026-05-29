package net.astrorbits.football.config.yacl

import dev.isxander.yacl3.api.ConfigCategory
import dev.isxander.yacl3.api.OptionGroup
import dev.isxander.yacl3.api.YetAnotherConfigLib
import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.config.client.FootballClientConfigHolder
import net.astrorbits.football.config.yacl.YaclOptionUtil.addDouble
import net.astrorbits.football.config.yacl.YaclOptionUtil.addInt
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

object FootballClientConfigScreen {
    fun create(parent: Screen?): Screen {
        var draft = FootballClientConfigHolder.current

        return YetAnotherConfigLib.createBuilder()
            .title(Component.translatable("yacl3.config.$MOD_ID.client.title"))
            .category(
                ConfigCategory.createBuilder()
                    .name(Component.translatable("yacl3.config.$MOD_ID.client.category.hud"))
                    .group(
                        OptionGroup.createBuilder()
                            .name(Component.translatable("yacl3.config.$MOD_ID.client.group.hud"))
                            .apply {
                                addDouble(
                                    "yacl3.config.$MOD_ID.client.hint_hide_extra_range",
                                    "yacl3.config.$MOD_ID.client.hint_hide_extra_range.desc",
                                    { draft.hintHideExtraRange },
                                    { v -> draft = draft.copy(hintHideExtraRange = v) },
                                    0.0..2.0,
                                )
                            }
                            .build(),
                    )
                    .group(
                        OptionGroup.createBuilder()
                            .name(Component.translatable("yacl3.config.$MOD_ID.client.group.render"))
                            .apply {
                                addDouble(
                                    "yacl3.config.$MOD_ID.client.render_stationary_speed_sqr",
                                    "yacl3.config.$MOD_ID.client.render_stationary_speed_sqr.desc",
                                    { draft.renderStationarySpeedSqr },
                                    { v -> draft = draft.copy(renderStationarySpeedSqr = v) },
                                    0.0..0.01,
                                    step = 0.0001,
                                )
                                addDouble(
                                    "yacl3.config.$MOD_ID.client.client_correction_threshold",
                                    "yacl3.config.$MOD_ID.client.client_correction_threshold.desc",
                                    { draft.clientCorrectionThreshold },
                                    { v -> draft = draft.copy(clientCorrectionThreshold = v) },
                                    0.0..2.0,
                                )
                            }
                            .build(),
                    )
                    .group(
                        OptionGroup.createBuilder()
                            .name(Component.translatable("yacl3.config.$MOD_ID.client.group.input"))
                            .apply {
                                addInt(
                                    "yacl3.config.$MOD_ID.client.dribble_hold_packet_interval",
                                    "yacl3.config.$MOD_ID.client.dribble_hold_packet_interval.desc",
                                    { draft.dribbleHoldPacketInterval },
                                    { v -> draft = draft.copy(dribbleHoldPacketInterval = v) },
                                    1..20,
                                )
                            }
                            .build(),
                    )
                    .build(),
            )
            .save { FootballClientConfigHolder.apply(draft) }
            .build()
            .generateScreen(parent)
    }

    private const val MOD_ID = NMBCTFootball.MOD_ID
}
