package net.astrorbits.football.network

/**
 * 客户端发送到服务端的足球动作类型。
 */
enum class FootballActionType {
    /** 传球（短按踢球键）。 */
    PASS,
    /** 射门（长按蓄力后松开踢球键）。 */
    SHOOT,
    /** 持续带球（按住带球键期间的心跳包）。 */
    DRIBBLE_HOLD,
    /** 停球（将球速归零）。 */
    TRAP,
    /** 挑球（将球向上挑起）。 */
    CHIP,
    /** 结束带球（松开带球键或状态中断）。 */
    DRIBBLE_END,
    /** 守门员接球（站立接住来球）。 */
    GK_CATCH,
    /** 守门员鱼跃扑救。 */
    GK_DIVE,
    /** 守门员拳击解围。 */
    GK_PUNCH,
    /** 守门员短抛手抛球。 */
    GK_THROW_SHORT,
    /** 守门员长抛手抛球（蓄力）。 */
    GK_THROW_LONG,
    /** 守门员将手中球放到脚下。 */
    GK_DROP,
    /** 主手持足球物品时，左键沿视线轻踢抛出。 */
    ITEM_THROW,
    /** 跑步中触发滑铲动作。 */
    SLIDE_TACKLE,
    /** 松开滑铲键后请求结束滑铲。 */
    SLIDE_TACKLE_END,
    /** 守门员鱼跃满蓄力保持期间的体力消耗心跳。 */
    GK_DIVE_CHARGE_DRAIN,
    /** 守门员打断鱼跃蓄力（如带球键取消）。 */
    GK_DIVE_CHARGE_CANCEL,
}
