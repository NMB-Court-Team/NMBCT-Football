package net.astrorbits.football.item

import net.astrorbits.football.Football
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.FootballSounds
import net.astrorbits.football.FootballParticles
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

class FootballItem(properties: Properties) : Item(properties) {
    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        if (level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        val player = context.player ?: return InteractionResult.PASS
        val spawnPos = context.clickedPos.relative(context.clickedFace)
        return placeFootball(level, player, context.hand, spawnPos)
    }

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        val blockHit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE)
        if (blockHit.type == HitResult.Type.MISS) {
            return InteractionResult.PASS
        }

        val spawnPos = blockHit.blockPos.relative(blockHit.direction)
        return placeFootball(level, player, hand, spawnPos)
    }

    private fun placeFootball(
        level: Level,
        player: Player,
        hand: InteractionHand,
        spawnBlockPos: BlockPos
    ): InteractionResult {
        val stack = player.getItemInHand(hand)
        val spawnVec = spawnPosition(spawnBlockPos)

        val football = Football(Football.ENTITY_TYPE, level)
        football.setPos(spawnVec.x, spawnVec.y, spawnVec.z)

        if (!level.noCollision(football)) {
            return InteractionResult.FAIL
        }

        if (!level.addFreshEntity(football)) {
            return InteractionResult.FAIL
        }

        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1)
        }

        FootballSounds.playFootballPlace(level, football.blockPosition(), player.random)
        FootballParticles.playFootballPlace(level, football.blockPosition())

        return InteractionResult.SUCCESS_SERVER
    }

    private fun spawnPosition(spawnBlockPos: BlockPos): Vec3 {
        return Vec3(
            spawnBlockPos.x + 0.5,
            spawnBlockPos.y + FootballPhysicsConfig.RADIUS,
            spawnBlockPos.z + 0.5
        )
    }
}
