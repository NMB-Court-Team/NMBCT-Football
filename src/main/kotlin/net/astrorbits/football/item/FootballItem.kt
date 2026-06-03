package net.astrorbits.football.item

import net.astrorbits.football.Football
import net.astrorbits.football.FootballParticles
import net.astrorbits.football.FootballSounds
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.util.FootballKickUtil
import net.astrorbits.football.util.Vec3Math
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

class FootballItem(properties: Properties) : Item(properties) {
    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        if (level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        val player = context.player ?: return InteractionResult.PASS
        return placeAtFeet(level, player, context.hand)
    }

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS
        }
        return placeAtFeet(level, player, hand)
    }

    private fun placeAtFeet(
        level: Level,
        player: Player,
        hand: InteractionHand,
    ): InteractionResult =
        spawnFootball(
            level = level,
            player = player,
            hand = hand,
            spawnVec = feetSpawnPosition(player),
            launchDirection = null,
        )

    private fun throwFromItem(
        level: Level,
        player: Player,
        hand: InteractionHand,
    ): InteractionResult {
        val look = Vec3Math.normalizeSafe(player.lookAngle)
        if (look.lengthSqr() < 1.0e-8) {
            return InteractionResult.PASS
        }
        return spawnFootball(
            level = level,
            player = player,
            hand = hand,
            spawnVec = throwSpawnPosition(player, look),
            launchDirection = look.scale(FOOTBALL_ITEM_THROW_FORCE),
        )
    }

    private fun spawnFootball(
        level: Level,
        player: Player,
        hand: InteractionHand,
        spawnVec: Vec3,
        launchDirection: Vec3?,
    ): InteractionResult {
        val stack = player.getItemInHand(hand)
        if (player.cooldowns.isOnCooldown(stack)) {
            return InteractionResult.PASS
        }

        val football = Football(Football.ENTITY_TYPE, level)
        football.setPos(spawnVec.x, spawnVec.y, spawnVec.z)

        if (!level.noCollision(football)) {
            return InteractionResult.FAIL
        }

        if (!level.addFreshEntity(football)) {
            return InteractionResult.FAIL
        }

        launchDirection?.let { direction ->
            val center = FootballParticles.centerOfFootball(football)
            val horizontal = Vec3Math.horizontal(direction)
            val kickPoint = FootballKickUtil.buildKickPoint(center, horizontal, 0.0)
            if (player is ServerPlayer) {
                football.recordActiveKick(player, direction)
            }
            football.kick(kickPoint, direction)
            if (player is ServerPlayer) {
                FootballSounds.playKick(player, FOOTBALL_ITEM_THROW_FORCE)
            }
            FootballParticles.playKick(level, center, FOOTBALL_ITEM_THROW_FORCE, direction)
        }

        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1)
        }

        if (launchDirection == null) {
            FootballSounds.playFootballPlace(level, football.blockPosition(), player.random)
            FootballParticles.playFootballPlace(level, football.blockPosition())
        }

        player.cooldowns.addCooldown(stack, ITEM_COOLDOWN_TICKS)
        return InteractionResult.SUCCESS_SERVER
    }

    private fun feetSpawnPosition(player: Player): Vec3 {
        val forward = Vec3Math.normalizeSafe(Vec3Math.horizontal(player.lookAngle))
        val offset = if (forward.lengthSqr() > 1.0e-8) forward.scale(0.35) else Vec3.ZERO
        return Vec3(
            player.x + offset.x,
            player.y + FootballPhysicsConfig.RADIUS,
            player.z + offset.z,
        )
    }

    private fun throwSpawnPosition(player: Player, look: Vec3): Vec3 {
        val eye = player.eyePosition
        return eye.add(look.scale(0.45))
    }

    companion object {
        private const val FOOTBALL_ITEM_THROW_FORCE = 1.0
        private const val ITEM_COOLDOWN_TICKS = 4

        fun tryThrowFromMainHand(player: ServerPlayer): InteractionResult {
            val stack = player.mainHandItem
            if (!stack.`is`(Items.FOOTBALL)) {
                return InteractionResult.PASS
            }
            val item = stack.item as? FootballItem ?: return InteractionResult.PASS
            return item.throwFromItem(player.level(), player, InteractionHand.MAIN_HAND)
        }
    }
}
