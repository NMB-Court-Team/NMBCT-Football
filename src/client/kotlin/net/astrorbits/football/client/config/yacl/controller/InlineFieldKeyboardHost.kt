package net.astrorbits.football.client.config.yacl.controller

import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent

/**
 * YACL [dev.isxander.yacl3.gui.controllers.ListEntryWidget] 只向 focused 子控件转发按键；
 * 内联数字框的焦点在 entryWidget 内部，需由 mixin 经此接口转发键盘输入。
 */
interface InlineFieldKeyboardHost {
    fun hasActiveInlineField(): Boolean
    fun handleKeyPressed(event: KeyEvent): Boolean
    fun handleCharTyped(event: CharacterEvent): Boolean
}
