package net.astrorbits.football.util

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.util.ProblemReporter
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.level.storage.TagValueOutput

object ValueIOUtil {
    fun createNbtOutput(
        errReporter: ProblemReporter,
        lookup: HolderLookup.Provider? = null,
        tag: CompoundTag = CompoundTag(),
    ): TagValueOutput {
        val ops = if (lookup != null) lookup.createSerializationContext(NbtOps.INSTANCE) else NbtOps.INSTANCE
        return TagValueOutput(errReporter, ops, tag)
    }

    fun createNbtInput(
        errReporter: ProblemReporter,
        lookup: HolderLookup.Provider,
        tag: CompoundTag,
    ): TagValueInput {
        return TagValueInput.create(errReporter, lookup, tag) as TagValueInput
    }
}