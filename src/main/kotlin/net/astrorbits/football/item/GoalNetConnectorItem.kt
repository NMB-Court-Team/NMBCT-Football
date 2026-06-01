package net.astrorbits.football.item

import net.astrorbits.football.item.GoalNetConnectorSounds
import net.astrorbits.football.block.GoalNetAnchorBlock
import net.astrorbits.football.entity.GoalNetEntity
import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.physics.GoalNetConfig
import net.astrorbits.football.util.GoalNetGeometry
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/**
 * 球网连接器：
 * - 右键：沿视线先做方块射线（距离 = [Player.blockInteractionRange]），首个方块为锚点则选点/建网；
 *   否则检测球网实体并调节松弛度；Shift+右键仅对空气时清空已选锚点。
 * - 左键球网销毁由 [net.astrorbits.football.GoalNetInteractions] 处理。
 */
class GoalNetConnectorItem(properties: Properties) : Item(properties) {

    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        if (level.isClientSide) return InteractionResult.SUCCESS
        val player = context.player as? ServerPlayer ?: return InteractionResult.PASS
        return handleUse(level, player, context.hand)
    }

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (level.isClientSide) return InteractionResult.SUCCESS
        val serverPlayer = player as? ServerPlayer ?: return InteractionResult.PASS
        // 指向球网实体时由 UseEntityCallback 处理，避免与 Item.use 重复触发（如 Shift+右键时误清空选点）。
        if (raycastGoalNet(serverPlayer, level) != null) {
            return InteractionResult.PASS
        }
        return handleUse(level, serverPlayer, hand)
    }

    fun handleUse(level: Level, player: ServerPlayer, hand: InteractionHand): InteractionResult {
        val blockHit = raycastBlock(player, level)
        if (blockHit.type != HitResult.Type.MISS) {
            val pos = blockHit.blockPos
            if (level.getBlockState(pos).block is GoalNetAnchorBlock) {
                return handleAnchorClick(level, player, pos, hand)
            }
        }

        val entityHit = raycastGoalNet(player, level)
        if (entityHit != null) {
            return handleNetEntityUse(player, entityHit.entity as GoalNetEntity)
        }

        if (player.isShiftKeyDown) {
            GoalNetConnectorState.clear(player.uuid)
            syncSelection(player)
            GoalNetConnectorSounds.playSelectionClear(player)
            player.sendOverlayMessage(Component.translatable("message.nmbct-football.goal_net.cleared"))
            return InteractionResult.SUCCESS_SERVER
        }
        return InteractionResult.PASS
    }

    private fun handleNetEntityUse(player: ServerPlayer, net: GoalNetEntity): InteractionResult {
        val newSlack = if (player.isShiftKeyDown) net.decreaseSlack() else net.increaseSlack()
        if (player.isShiftKeyDown) {
            GoalNetConnectorSounds.playSlackDecrease(player, net)
        } else {
            GoalNetConnectorSounds.playSlackIncrease(player, net)
        }
        val percent = Math.round(newSlack * 100.0).toInt()
        player.sendOverlayMessage(Component.translatable("message.nmbct-football.goal_net.slack", percent))
        return InteractionResult.SUCCESS_SERVER
    }

    private fun handleAnchorClick(level: Level, player: ServerPlayer, pos: BlockPos, hand: InteractionHand): InteractionResult {
        val uuid = player.uuid
        if (GoalNetConnectorState.contains(uuid, pos)) {
            GoalNetConnectorSounds.playAnchorDuplicate(player, pos)
            player.sendOverlayMessage(Component.translatable("message.nmbct-football.goal_net.duplicate_click"))
            return InteractionResult.SUCCESS_SERVER
        }

        val count = GoalNetConnectorState.addPoint(uuid, pos)
        syncSelection(player)
        if (count < 4) {
            GoalNetConnectorSounds.playAnchorSelect(player, pos)
            player.sendOverlayMessage(
                Component.translatable("message.nmbct-football.goal_net.selected", count, 4)
            )
            return InteractionResult.SUCCESS_SERVER
        }

        val points = GoalNetConnectorState.getPoints(uuid)
        val anchorPositions = GoalNetGeometry.resolveAnchorPositions(level, points)
        if (anchorPositions == null) {
            GoalNetConnectorState.clear(uuid)
            syncSelection(player)
            GoalNetConnectorSounds.playNetFail(player)
            player.sendOverlayMessage(Component.translatable(GoalNetGeometry.Failure.INVALID_ANCHOR.translationKey))
            return InteractionResult.SUCCESS_SERVER
        }
        when (val result = GoalNetGeometry.validate(anchorPositions)) {
            is GoalNetGeometry.Result.Success -> {
                if (GoalNetMaterialCost.requiresCost(player)) {
                    val connectorHand = GoalNetMaterialCost.findConnectorHand(player, hand)
                    if (connectorHand == null) {
                        GoalNetConnectorSounds.playNetFail(player)
                        player.sendOverlayMessage(
                            Component.translatable("message.nmbct-football.goal_net.fail.no_connector")
                        )
                        return InteractionResult.SUCCESS_SERVER
                    }
                    val connectorStack = player.getItemInHand(connectorHand)
                    if (!GoalNetMaterialCost.connectorHasDurability(connectorStack)) {
                        GoalNetConnectorSounds.playNetFail(player)
                        player.sendOverlayMessage(
                            Component.translatable("message.nmbct-football.goal_net.fail.no_durability")
                        )
                        return InteractionResult.SUCCESS_SERVER
                    }
                    if (!GoalNetMaterialCost.hasEnoughString(player)) {
                        GoalNetConnectorSounds.playNetFail(player)
                        player.sendOverlayMessage(
                            Component.translatable("message.nmbct-football.goal_net.fail.not_enough_string")
                        )
                        return InteractionResult.SUCCESS_SERVER
                    }
                }
                val entity = GoalNetEntity(GoalNetEntity.ENTITY_TYPE, level)
                if (entity.setup(level, points, GoalNetConfig.DEFAULT_SLACK) && level.addFreshEntity(entity)) {
                    if (GoalNetMaterialCost.requiresCost(player)) {
                        val connectorHand = GoalNetMaterialCost.findConnectorHand(player, hand)!!
                        GoalNetMaterialCost.tryConsumeString(player)
                        GoalNetMaterialCost.damageConnector(player, connectorHand)
                    }
                    GoalNetConnectorSounds.playNetCreated(player)
                    player.sendOverlayMessage(Component.translatable("message.nmbct-football.goal_net.created"))
                } else {
                    GoalNetConnectorSounds.playNetFail(player)
                    player.sendOverlayMessage(Component.translatable("message.nmbct-football.goal_net.fail.spawn"))
                }
            }
            is GoalNetGeometry.Result.Failed -> {
                GoalNetConnectorSounds.playNetFail(player)
                player.sendOverlayMessage(Component.translatable(result.failure.translationKey))
            }
        }
        GoalNetConnectorState.clear(uuid)
        syncSelection(player)
        return InteractionResult.SUCCESS_SERVER
    }

    private fun syncSelection(player: ServerPlayer) {
        FootballNetworking.sendGoalNetConnectorSelection(player, GoalNetConnectorState.getPoints(player.uuid))
    }

    companion object {
        /** 沿视线检测首个方块（距离 = block_interaction_range）。 */
        fun raycastBlock(player: Player, level: Level): BlockHitResult {
            val range = player.blockInteractionRange()
            val eye = player.eyePosition
            val end = eye.add(player.getViewVector(1.0f).scale(range))
            val context = ClipContext(
                eye,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player,
            )
            return level.clip(context)
        }

        /** 沿视线检测首个可交互的球网实体（距离 = entity_interaction_range）。 */
        fun raycastGoalNet(player: Player, level: Level): EntityHitResult? {
            val range = player.entityInteractionRange()
            val look = player.getViewVector(1.0f)
            val eye = player.eyePosition
            val end = eye.add(look.scale(range))
            val searchBox = player.boundingBox.expandTowards(look.scale(range)).inflate(1.0)
            return ProjectileUtil.getEntityHitResult(
                player,
                eye,
                end,
                searchBox,
                { entity -> entity is GoalNetEntity && entity.isPickable && entity != player },
                range,
            )
        }
    }
}
