# 配置总览

NMBCT Football 有三类主要配置：

| 文件 | 生效位置 | 作用 |
| --- | --- | --- |
| `nmbct-football-match.json` | 服务端，支持世界专用覆盖 | 比赛规则、队名、球场几何、出生点、辅助功能 |
| `nmbct-football-server.json` | 服务端 | 足球物理、踢球/带球、守门员、体力、粒子、滑铲等权威手感 |
| `nmbct-football-client.json` | 客户端本机 | HUD、渲染距离、球网渲染模式、加速疾跑按键模式 |

## 生成和保存

- 缺少配置文件时，模组会按默认值生成。
- 通过 GUI 保存后会写回 JSON。
- JSON 解析失败时会回退默认值，并在日志中打印警告。
- 服务器配置会同步给在线客户端，但客户端不会把服务端同步值写入本地文件。

## 比赛配置：`nmbct-football-match.json`

游戏内入口：

- `/match setup`：队伍、赛制、时间、辅助功能。
- `/match config`：球场几何、球门、边线、出生点。

路径优先级：

1. `<world>/nmbct-football-match.json`
2. `<Fabric 配置目录>/nmbct-football-match.json`

### 顶层字段

| 字段 | 含义 |
| --- | --- |
| `field_config_version` | 球场配置版本，推荐 `2` |
| `team_a_name`, `team_b_name` | 双方队名 |
| `rules` | 比赛规则 |
| `accessibility` | 辅助功能 |
| `goal_a`, `goal_b` | 两座球门和各自半场区域 |
| `sideline_a`, `sideline_b` | 两条边线 |
| `kick_off` | 开球点 |
| `center_circle_radius` | 中圈半径 |
| `corner_kick_penalty_area_radius` | 角球限制半径 |
| `throw_in_penalty_area_radius` | 界外球限制半径 |
| `free_kick_distance_radius` | 任意球防守距离 |
| `team_a_spawn`, `team_b_spawn` | 双方出生点 |

### `rules`

| 字段 | 默认值 | 含义 |
| --- | --- | --- |
| `half_time_minutes` | `5` | 半场分钟数 |
| `enable_offside` | `true` | 是否启用越位 |
| `enable_stoppage_time` | `false` | 是否启用补时 |
| `stoppage_time_max_minutes` | `3` | 补时上限 |
| `enable_extra_time` | `false` | 是否启用加时 |
| `extra_time_half_minutes` | `3` | 加时每半场分钟数 |
| `enable_penalty_shootout` | `false` | 平局时是否进入点球大战 |
| `post_goal_ball_reset_delay_seconds` | `3` | 进球后重置球的延迟 |
| `enable_pre_match_preparation` | `true` | 是否启用赛前准备阶段 |
| `pre_match_preparation_minutes` | `2` | 赛前准备分钟数 |

如果 `half_time_minutes = 0`，并且启用点球大战，可以用于测试开赛直接进入点球大战。

### `accessibility`

| 字段 | 默认值 | 含义 |
| --- | --- | --- |
| `enable_football_position_indicator` | `false` | 比赛中为客户端显示足球方位指示 |

这个选项只影响 HUD，不改变物理、判例或服务器逻辑。

## 服务端配置：`nmbct-football-server.json`

游戏内入口：`/football config`

服务端配置是权威配置，会影响所有玩家的实际玩法。顶层结构：

```json
{
  "physics": {},
  "player_input": {},
  "goalkeeper": {},
  "particles": {},
  "stamina_mechanism": {}
}
```

### `physics`

| 分组 | 常用字段 | 说明 |
| --- | --- | --- |
| `core` | `radius`, `mass`, `gravity`, `air_drag`, `ground_friction` | 足球尺寸、重力、空气阻力、地面摩擦 |
| `collision` | `restitution`, `wall_restitution`, `wall_spin_retention`, `ground_settle_vy` | 与地面、墙、方块碰撞后的反弹和旋转 |
| `kick` | `kick_force_scale`, `kick_moving_lateral_damp` | 踢球冲量换算和移动中侧向修正 |

调参建议：

- 球太飘：提高 `gravity` 或降低 `air_drag`。
- 球滚得太远：降低 `ground_friction`。
- 撞墙太弹：降低 `wall_restitution`。

### `player_input`

| 分组 | 说明 |
| --- | --- |
| `kick` | 踢球距离、传球力度、射门最小/最大力度、挑球参数 |
| `dribble` | 带球目标距离、控制范围、速度匹配、修正强度 |
| `charge` | 蓄力时长、完美蓄力窗口、弧线球输入窗口 |
| `collision` | 球与球员互相推动、回弹和接触宽限 |
| `slide` | 滑铲速度、持续、冷却、撞人、铲球触球力度 |

常用字段：

| 字段路径 | 默认值 | 含义 |
| --- | --- | --- |
| `player_input.kick.player_kick_range` | `2.5` | 玩家可操作足球距离 |
| `player_input.kick.pass_force` | `1.5` | 短按传球力度 |
| `player_input.kick.shoot_force_min` | `2.0` | 射门最小力度 |
| `player_input.kick.shoot_force_max` | `4.0` | 射门最大力度 |
| `player_input.dribble.dribble_target_distance` | `1.2` | 带球时足球目标距离 |
| `player_input.charge.perfect_charge_force_bonus` | `1.08` | 完美蓄力力度倍率 |
| `player_input.slide.slide_tackle_cooldown_seconds` | `3.0` | 滑铲冷却秒数 |
| `player_input.slide.slide_ball_kick_force` | `3.0` | 滑铲踢到球时的力度 |

### `goalkeeper`

| 分组 | 说明 |
| --- | --- |
| `catch` | 接球范围、可接球最大球速、持球位置、抢球保护 |
| `dive.behavior` | 鱼跃持续、冷却、判定范围、扑救/接住判定 |
| `dive.pitch` | 视角俯仰对鱼跃高度和前冲的影响 |
| `dive.impulse` | 蓄力鱼跃起跳和持续冲量 |
| `dive.actions` | 击球、短抛、长抛力度 |

常用字段：

| 字段路径 | 默认值 | 含义 |
| --- | --- | --- |
| `goalkeeper.catch.catch_range` | `3.5` | 接球范围 |
| `goalkeeper.catch.catch_max_speed` | `1.8` | 可直接接住的最大球速 |
| `goalkeeper.catch.hold_steal_protection_ticks` | `200` | 比赛中门将持球后防抢保护，20 tick 约 1 秒 |
| `goalkeeper.dive.behavior.dive_range` | `3.0` | 鱼跃判定范围 |
| `goalkeeper.dive.behavior.dive_cooldown_ticks` | `24` | 鱼跃冷却 |
| `goalkeeper.dive.actions.throw_long_force_max` | `3.2` | 长抛最大力度 |

### `stamina_mechanism`

体力配置会影响疾跑、跳跃、滑铲、守门员鱼跃蓄力和加速疾跑。

| 字段 | 默认值 | 合法范围或说明 |
| --- | --- | --- |
| `max_stamina` | `1000` | `50` 到 `5000` |
| `jump_cost` | `60` | `0` 到 `200` |
| `sprint_drain_per_second` | `10` | `0` 到 `50` |
| `recovery_delay_seconds` | `1` | `0.05` 到 `5` |
| `recovery_per_second` | `20` | `0` 到 `100` |
| `half_time_recovery_fraction` | `0.6` | 半场恢复比例 |
| `goal_recovery_fraction` | `0.15` | 进球后恢复比例 |
| `speed_tiers` | 见下方 | 低体力移速倍率表 |

默认移速档位：

```json
"speed_tiers": [
  { "stamina_fraction": 0.0, "speed_multiplier": 0.6 },
  { "stamina_fraction": 0.1, "speed_multiplier": 0.7 },
  { "stamina_fraction": 0.4, "speed_multiplier": 0.85 },
  { "stamina_fraction": 0.8, "speed_multiplier": 0.95 }
]
```

`stamina_fraction` 是体力比例上限。当前体力比例严格小于该值时，使用对应 `speed_multiplier`。档位需要按 `stamina_fraction` 升序，速度倍率应随体力增加不下降。

### `particles`

| 分组 | 说明 |
| --- | --- |
| `bounce` | 足球弹地、撞墙粒子的触发阈值和扩散 |
| `counts` | 踢球、带球、停球、门将动作粒子数量 |
| `high_speed_drag` | 高速球拖尾粒子 |
| `kick_visual` | 踢球扫腿和云环视觉参数 |

如果服务器压力较大，可以优先降低 `particles.counts.*_count` 和 `high_speed_drag.*_count_*`。

## 客户端配置：`nmbct-football-client.json`

客户端配置只影响本机显示和输入习惯，不改变服务端判定。

| 字段 | 默认值 | 含义 |
| --- | --- | --- |
| `hint_hide_extra_range` | `0.4` | 按键提示隐藏额外距离 |
| `render_stationary_speed_sqr` | `0.0001` | 判断足球静止渲染的速度平方阈值 |
| `client_correction_threshold` | `0.25` | 客户端校正阈值 |
| `ball_render_dist` | `128.0` | 足球最远渲染距离 |
| `ball_billboard_ratio` | `0.62` | 超过渲染距离一定比例后切换面片渲染 |
| `dribble_hold_packet_interval` | `2` | 按住带球时客户端发包间隔 |
| `goal_net_render_mode` | `auto` | 球网渲染模式 |
| `boost_sprint_input_mode` | `hold` | 加速疾跑输入模式 |

`goal_net_render_mode` 可选：

- `auto`
- `vanilla_compat`
- `shader_compat`

`boost_sprint_input_mode` 可选值取决于当前版本的客户端配置界面，通常在“按住”和“切换”之间选择。推荐通过 Mod Menu 或客户端配置界面修改。

## 推荐工作流

1. 先用 `/match config` 配好球场，测试边线、底线和进球。
2. 再用 `/match setup` 调整赛制。
3. 最后用 `/football config` 调手感。
4. 每次大改配置后重启服务器或执行一次保存，让 JSON 落盘。
5. 把稳定的 `nmbct-football-match.json` 放入世界根目录，便于随存档一起迁移。

## 排错

| 现象 | 优先检查 |
| --- | --- |
| 服务器启动后配置被默认值覆盖 | JSON 语法是否有效，字段名是否拼写正确 |
| 改了全局配置但没生效 | 世界根目录是否存在同名 `nmbct-football-match.json` |
| 客户端显示和别人不一样 | 检查本机 `nmbct-football-client.json` |
| 射门、带球、守门员手感全服一致 | 检查服务端 `nmbct-football-server.json`，客户端本地改不了权威手感 |
| 低体力速度异常 | 检查 `speed_tiers` 是否升序、倍率是否随体力上升不下降 |

