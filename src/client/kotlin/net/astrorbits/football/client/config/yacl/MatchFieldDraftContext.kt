package net.astrorbits.football.client.config.yacl

import dev.isxander.yacl3.api.Option
import net.astrorbits.football.match.MatchConfig

/** 场地 YACL 草稿与选项刷新：快捷按钮写入 draft 后同步各 Option 的 pending 值。 */
class MatchFieldDraftContext(var draft: MatchConfig) {
    private val tracked = mutableListOf<Option<*>>()

    fun track(option: Option<*>) {
        tracked += option
    }

    fun syncPendingFromDraft() {
        tracked.forEach { it.forgetPendingValue() }
    }
}
