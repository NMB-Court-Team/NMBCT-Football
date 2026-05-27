# 足球物理模拟原理

本文档说明 NMBCT Football 模组中足球实体（`Football`）的物理模拟设计、每 tick 的计算流程，以及服务端与客户端的协作方式。

## 设计目标

- **自定义刚体运动**：不依赖原版实体重力与速度积分，由模组自行维护线速度与角速度。
- **完整方块碰撞**：位移通过 `Entity.move(MoverType.SELF, …)` 完成，复用 Minecraft 的 AABB 与方块碰撞检测。
- **可踢、可弹、可滚**：支持偏心踢球（产生扭矩）、地面弹性、摩擦与滚动耦合。
- **球门网（蜘蛛网）**：球进入蜘蛛网区域时显著减速，可用于两端球门。
- **多人同步**：服务端权威；客户端读取同步速度并积分朝向，不再本地重复位移模拟。

## 架构概览

物理计算集中在 `util` 与 `physics` 包；`Football` 实体负责 Minecraft 生命周期、碰撞、`move` 调用与网络同步。

```mermaid
flowchart TB
    subgraph state [状态]
        FPS[FootballPhysicsState]
        FPC[FootballPhysicsConfig]
    end

    subgraph sim [每 tick 模拟]
        Air[applyAirForces]
        Move[Entity.move]
        Col[resolveCollisions]
        Web[CobwebUtil]
        Ori[integrateOrientation]
    end

    subgraph entity [Football 实体]
        Tick[tick]
        Kick[kick 仅服务端]
        Sync[SynchedEntityData]
    end

    Tick --> Air --> Move --> Col --> Web --> Ori
    FPS --> sim
    FPC --> sim
    Kick --> FPS
    Ori --> Sync
```

### 源码结构

| 路径                                       | 职责                        |
|------------------------------------------|---------------------------|
| `physics/FootballPhysicsState.kt`        | 线速度、角速度、接地/蛛网标志、视觉朝向（四元数） |
| `physics/FootballPhysicsConfig.kt`       | 质量、弹性、摩擦、重力等可调常量          |
| `util/FootballPhysicsSimulator.kt`       | 踢球、空气力、碰撞入口、朝向积分、滚动方向     |
| `util/CollisionUtil.kt`                  | 地面反弹、摩擦、墙体衰减、滚动耦合         |
| `util/CobwebUtil.kt`                     | 检测 AABB 是否与蜘蛛网相交并施加阻力     |
| `util/Vec3Math.kt` / `QuaternionMath.kt` | 向量与旋转工具                   |
| `Football.kt`                            | `tick`、`kick`、同步、存档       |

## 状态变量

`FootballPhysicsState` 在服务端与客户端各有一份本地副本（客户端仅用于渲染朝向积分）。

| 字段                | 含义                       | 是否同步到客户端              |
|-------------------|--------------------------|-----------------------|
| `linearVelocity`  | 线速度（blocks/tick）         | 是（`DATA_LINEAR_VEL`）  |
| `angularVelocity` | 角速度向量；方向为转轴，模长为 rad/tick | 是（`DATA_ANGULAR_VEL`） |
| `onGround`        | 本 tick 是否视为接地            | 是（`DATA_ON_GROUND`）   |
| `inCobweb`        | 本 tick 是否在蜘蛛网内           | 否（本地每 tick 重算）        |
| `orientation`     | 渲染用四元数朝向                 | 否（由角速度在两端分别积分）        |

实体初始化时设置 `setNoGravity(true)`，避免与自定义重力叠加；`requiresPrecisePosition = true` 以减少原版位置插值与自定义物理冲突。

## 每 tick 计算流程

以下顺序在 **服务端** `Football.serverTick()` 中执行；客户端每 tick 从 `SynchedEntityData` 读取线速度/角速度，仅积分 `orientation`。

### 1. 空气力与重力（`applyAirForces`）

在位移**之前**对线速度、角速度做欧拉步进：

1. **重力**：`v_y ← v_y - GRAVITY`（默认 `0.04` blocks/tick²）
2. **空气阻力**：`v ← v × AIR_DRAG`（默认 `0.99`/tick）
3. **自转衰减**：`ω ← ω × SPIN_DRAG`（默认 `0.995`/tick）

### 2. 位移与碰撞检测（`move`）

```text
deltaMovement = linearVelocity
move(MoverType.SELF, linearVelocity)
```

`move` 会更新实体位置，并设置 `horizontalCollision`、`verticalCollisionBelow`、`onGround()` 等标志，供下一步使用。方块台阶、墙体、地面均由原版碰撞系统处理。

### 3. 碰撞响应（`CollisionUtil.resolveCollisions`）

根据 `move` 结果修正速度（不再次移动位置）：

| 条件                    | 处理                                                   |
|-----------------------|------------------------------------------------------|
| 接地且 `v_y < 0`         | 竖直反弹：`v_y ← -v_y × RESTITUTION`                      |
| 接地                    | 水平摩擦：`v_x, v_z × GROUND_FRICTION`；角速度 `ω × GROUND_SPIN_FRICTION`；双向滚动耦合 |
| `horizontalCollision` | 对比本 tick **意图位移**与**实际位移**：被挡轴的速度分量沿墙法向反射（`v ← -v × WALL_RESTITUTION`），擦墙轴仅衰减 |
| 贴墙几乎不动              | 跳过滚动耦合，并对 `ω` 施加 `STUCK_SPIN_DRAG`，避免从自转持续泵入线速度 |

**滚动耦合**（接地且未贴墙卡住时）：无滑滚动近似下，水平线速度与角速度应满足：

```text
v_x ≈ -r · ω_z
v_z ≈  r · ω_x
```

其中 `r` 为 `RADIUS`（`0.25`）。每 tick **双向**拉近线速度与水平自转（`ROLL_COUPLING`，默认 `0.15`），避免只把 `ω` 灌进 `v` 导致越滚越快。水平速度低于 `STOP_SPEED_SQR` 时清零水平 `v` 与水平 `ω`。

### 4. 蜘蛛网阻力（`CobwebUtil`）

若实体 AABB 与任意 `Blocks.COBWEB` 方块相交，对速度施加每 tick 乘数（与原版蜘蛛网量级一致）：

| 分量              | 系数       |
|-----------------|----------|
| 水平 `v_x`, `v_z` | `× 0.25` |
| 竖直 `v_y`        | `× 0.05` |
| 角速度 `ω`         | `× 0.5`  |

自定义物理不会自动读取原版 `stuckSpeedMultiplier`，因此必须显式实现上述逻辑，球门网方可生效。

### 5. 姿态积分（`integrateOrientation`）

用当前 `angularVelocity` 对四元数 `orientation` 做小步旋转积分（`QuaternionMath.integrate`），供客户端渲染插值。渲染时在 tick 初将 `previousOrientation` 设为积分前的朝向，再用 `slerp` 做 `partialTick` 插值。

### 6. 写回与同步

- `deltaMovement = linearVelocity`（便于原版速度相关数据包）
- **仅服务端**：`entityData` 写入线速度、角速度、`onGround`

## 踢球（`kick`）

仅在**服务端**调用；客户端通过同步数据看到结果。

```text
冲量 F = direction × KICK_FORCE_SCALE   // direction 模长 = 命令 force；默认 scale=0.18
Δv = F / MASS
Δω = (kickPoint - 球心) × F / INERTIA
```

`force=1` 时线速增量约 **0.4 blocks/tick**（原先约 2.2）；`force=3` 约 1.2，适合中等射门力度。

- `kickPoint`：力的作用点（世界坐标）；水平方向在球心后方偏移一个半径。命令 `height` 为相对球心的竖直偏移（格，0=赤道）；偏高/偏低会在水平踢球时产生额外滚动扭矩（仍抑制绕 Y 轴偏航）。
- 踢球后根据水平线速度设置 **滚动自转** `ω_x = v_z/r`、`ω_z = -v_x/r`，`ω_y = 0`。
- `direction`：冲量向量，其长度即“力”的大小。

踢球后立即同步 `SynchedEntityData` 并调用 `syncPacketPositionCodec`。

## 滚动方向（`getRollingDirection`）

返回**水平单位向量**（Y = 0），长度为 0 时返回 `Vec3.ZERO`。优先级如下：

1. 若水平线速度足够大 → 归一化 `(v_x, 0, v_z)`
2. 若接地且线速度很小但有自转 → 由 `ω` 推导无滑滚动方向：`(-r·ω_z, 0, r·ω_x)`
3. 若仍不足 → 使用 `up × ω` 的水平分量
4. 否则 → `ZERO`

可用于玩法逻辑（例如判断球向哪边滚、是否进门等）。

## 服务端与客户端

```mermaid
sequenceDiagram
    participant S as 服务端
    participant C as 客户端

  Note over S: tick: 模拟 + move + 写 SynchedEntityData
  S->>C: 同步线速度/角速度/接地
  Note over C: tick: 相同模拟（预测）
  alt 速度误差 > CLIENT_CORRECTION_THRESHOLD
    C->>C: 对齐服务端速度 + lerpMotion
  end
```

| 侧       | 行为                                                                                                    |
|---------|-------------------------------------------------------------------------------------------------------|
| **服务端** | 权威模拟；`kick` 仅在此执行；每 tick 写入 `SynchedEntityData`                                                       |
| **客户端** | 每 tick 读取同步速度；渲染位置用 `xOld + v·partialTick` 外推；朝向用 `ω·partialTick` 积分；不在帧内重复同步 |

`orientation` 不同步：两端各自用相同 `angularVelocity` 积分，在一般情况下与预测一致。

## 存档

`readAdditionalSaveData` / `addAdditionalSaveData` 持久化：

- `lv_x/y/z`：线速度
- `av_x/y/z`：角速度
- `on_ground`：接地标志

## 参数调优

所有常量定义在 [`FootballPhysicsConfig.kt`](src/main/kotlin/net/astrorbits/football/physics/FootballPhysicsConfig.kt)，字段均附有中文 KDoc。常见调参方向：

| 现象       | 可调整项                                            |
|----------|-------------------------------------------------|
| 弹跳太高/太低  | `RESTITUTION`                                   |
| 地面滚不远    | `GROUND_FRICTION`（增大）、`ROLL_COUPLING`（增大）       |
| 空中飞太远    | `AIR_DRAG`（减小）、`GRAVITY`（增大）                    |
| 球门网太粘/太滑 | `COBWEB_HORIZONTAL_DRAG`、`COBWEB_VERTICAL_DRAG` |
| 撞墙反弹太强   | `WALL_RESTITUTION`                              |
| 高延迟下球路跳变 | `CLIENT_CORRECTION_THRESHOLD`                   |

## 与原版行为的差异

- **不使用**原版 `applyGravity()`；重力在 `applyAirForces` 中手动施加。
- **不依赖**原版蜘蛛网对 `deltaMovement` 的修改；蛛网效果由 `CobwebUtil` 显式处理。
- 速度以 `FootballPhysicsState.linearVelocity` 为准；`move` 之后可能微调该状态，并写回 `deltaMovement` 以兼容数据包。

## 相关命令与物品（便于测试）

- `/football summon`：在命令来源处生成足球
- `/football kick <force>`：水平踢球（`height=0`，`angle=0°`）
- `/football kick <force> <height>`：指定踢击点相对球心的竖直偏移（格）
- `/football kick <force> <height> <angle>`：`angle` 为相对水平面的仰角（度，0° 水平，正值上挑，负值下压，约 -90~90）
- 足球物品右键：在瞄准方块表面放置足球实体

## 客户端渲染

足球实体使用 **物品模型 + 物理四元数** 绘制，不在渲染器内重复积分角速度。

- **资源**：`assets/nmbct-football/models/item/football.json` 等，经 `ItemModelResolver.updateForNonLiving(..., ItemDisplayContext.GROUND, entity)` 解析为 `ItemStackRenderState`。
- **位置**：静止时（|v|² < `RENDER_STATIONARY_SPEED_SQR`）用原版 `getPosition` 插值；运动时用 `xOld + v·partialTick` 外推。
- **朝向**：`getOrientation(partialTick)` 从 `previousOrientation` 按 `ω·partialTick` 积分。
- **矩阵栈**：`PoseStack.use { }`（`client/PoseStackExtensions.kt`）包裹平移与旋转，避免遗漏 `popPose`。
- **管线（MC 26.1+）**：实体渲染走 `submit` + `SubmitNodeCollector`，物品层调用 `ItemStackRenderState.submit`；Y 偏移为碰撞半径 `RADIUS`（0.25），与 AABB 中心对齐。

若模型偏移或大小不对，优先调 `FootballRenderer` 内 `translate` 或 `ItemDisplayContext`；若旋转与运动不一致，应检查 `Football.tick` 中的 `integrateOrientation` 与同步，而非渲染器。

---

*文档版本与代码同步；修改物理逻辑时请一并更新本文档。*
