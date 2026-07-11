# 配置总览

语言：[English](Configuration-Guide) | **中文**

NMBCT Football 有三类主要配置：

| 文件 | 生效位置 | 作用 |
| --- | --- | --- |
| `nmbct-football-match.json` | 服务端，支持世界专用覆盖 | 比赛规则、队名、球场几何、出生点、辅助功能 |
| `nmbct-football-server.json` | 服务端 | 权威物理、踢球、带球、守门员、体力、粒子、滑铲 |
| `nmbct-football-client.json` | 本地客户端 | HUD、渲染距离、球网渲染模式、加速疾跑输入模式 |

## 保存和读取

- 缺少配置文件时，模组会按默认值生成。
- 游戏内配置界面保存后会写回 JSON。
- JSON 解析失败时会回退默认值，并在日志中打印警告。
- 服务端配置会同步给在线客户端，但同步值不会写入客户端本地文件。

## 比赛配置：`nmbct-football-match.json`

游戏内入口：

- `/match setup`：队伍、比赛时长、加时、点球、越位、辅助功能。
- `/match config`：球场几何、球门、边线、出生点。

读取优先级：

1. `<world>/nmbct-football-match.json`
2. `<Fabric 配置目录>/nmbct-football-match.json`

常用字段：

| 字段 | 含义 |
| --- | --- |
| `field_config_version` | 球场配置版本，推荐 `2` |
| `team_a_name`, `team_b_name` | 队名 |
| `rules` | 比赛规则 |
| `accessibility` | 辅助功能 |
| `goal_a`, `goal_b` | 两座球门和半场区域 |
| `sideline_a`, `sideline_b` | 两条边线 |
| `kick_off` | 中圈开球点 |
| `team_a_spawn`, `team_b_spawn` | 双方出生点 |

`rules` 常用字段：

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
| `enable_pre_match_preparation` | `true` | 是否启用赛前准备 |
| `pre_match_preparation_minutes` | `2` | 赛前准备分钟数 |

辅助功能：

| 字段 | 默认值 | 含义 |
| --- | --- | --- |
| `enable_football_position_indicator` | `false` | 比赛中显示足球方位指示 |

## 服务端配置：`nmbct-football-server.json`

游戏内入口：`/football config`

顶层结构：

```json
{
  "physics": {},
  "player_input": {},
  "goalkeeper": {},
  "particles": {},
  "stamina_mechanism": {}
}
```

| 分组 | 说明 |
| --- | --- |
| `physics` | 足球尺寸、重力、阻力、摩擦、碰撞反弹、踢球冲量 |
| `player_input` | 踢球、传球、射门、带球、蓄力、弧线球、滑铲 |
| `goalkeeper` | 接球、持球、鱼跃、击球、短抛、长抛 |
| `stamina_mechanism` | 最大体力、消耗、回复、低体力移速、动作消耗 |
| `particles` | 弹地、踢球、高速拖尾、守门员动作粒子 |

常用字段：

| 字段路径 | 默认值 | 含义 |
| --- | --- | --- |
| `player_input.kick.player_kick_range` | `2.5` | 玩家可操作足球距离 |
| `player_input.kick.pass_force` | `1.5` | 短按传球力度 |
| `player_input.kick.shoot_force_min` | `2.0` | 射门最小力度 |
| `player_input.kick.shoot_force_max` | `4.0` | 射门最大力度 |
| `player_input.dribble.dribble_target_distance` | `1.2` | 带球目标距离 |
| `player_input.slide.slide_tackle_cooldown_seconds` | `3.0` | 滑铲冷却 |
| `goalkeeper.catch.catch_range` | `3.5` | 守门员接球范围 |
| `goalkeeper.catch.hold_steal_protection_ticks` | `200` | 门将持球防抢保护 |

体力档位示例：

```json
"speed_tiers": [
  { "stamina_fraction": 0.0, "speed_multiplier": 0.6 },
  { "stamina_fraction": 0.1, "speed_multiplier": 0.7 },
  { "stamina_fraction": 0.4, "speed_multiplier": 0.85 },
  { "stamina_fraction": 0.8, "speed_multiplier": 0.95 }
]
```

`stamina_fraction` 是体力比例上限。档位需要升序，速度倍率应随体力增加不下降。

## 客户端配置：`nmbct-football-client.json`

客户端配置只影响本机显示和输入习惯，不改变服务端判定。

| 字段 | 默认值 | 含义 |
| --- | --- | --- |
| `hint_hide_extra_range` | `0.4` | 按键提示隐藏额外距离 |
| `render_stationary_speed_sqr` | `0.0001` | 判断足球静止渲染的速度平方阈值 |
| `client_correction_threshold` | `0.25` | 客户端校正阈值 |
| `ball_render_dist` | `128.0` | 足球最远渲染距离 |
| `ball_billboard_ratio` | `0.62` | 切换面片渲染的距离比例 |
| `dribble_hold_packet_interval` | `2` | 按住带球时客户端发包间隔 |
| `goal_net_render_mode` | `auto` | 球网渲染模式 |
| `boost_sprint_input_mode` | `hold` | 加速疾跑输入模式 |

## 排错

| 现象 | 优先检查 |
| --- | --- |
| 配置回退默认值 | JSON 语法和字段名 |
| 全局比赛配置没生效 | 世界目录是否有同名 `nmbct-football-match.json` |
| 客户端显示不同 | 本机 `nmbct-football-client.json` |
| 全服手感一致 | 服务端 `nmbct-football-server.json`，客户端不能覆盖权威玩法 |
| 低体力速度异常 | `speed_tiers` 是否升序、倍率是否不下降 |
