package net.astrorbits.football.client.config.yacl

import dev.isxander.yacl3.api.ButtonOption
import dev.isxander.yacl3.api.OptionDescription
import dev.isxander.yacl3.api.OptionGroup
import net.minecraft.network.chat.Component

fun OptionGroup.Builder.addFieldButton(
    ctx: MatchFieldDraftContext,
    textKey: String,
    action: () -> Unit,
) {
    option(
        ButtonOption.createBuilder()
            .name(Component.empty())
            .text(Component.translatable(textKey))
            .description(OptionDescription.of(Component.translatable(MatchYaclDesc.desc(textKey))))
            .action { _, _ ->
                action()
                ctx.syncPendingFromDraft()
            }
            .build(),
    )
}
