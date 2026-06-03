# 柠檬杯风驰足球-模组版

***关注柠檬杯法庭地图组谢谢喵***

## 简介

《柠檬杯风驰足球》是一个在 Minecraft 中模拟足球物理与比赛规则的模组：自定义球体物理、踢球/带球/守门员操作、体力与对抗机制，并由服务端监督比赛流程。

玩家分两队对抗，使用按键与道具操控足球，射门得分。场员与守门员操作、物理细节见 [FOOTBALL_PHYSICS.md](./FOOTBALL_PHYSICS.md)；比赛阶段、计时与判例见 [FOOTBALL_MATCH.md](./FOOTBALL_MATCH.md)；球门网见 [GOAL_NET.md](./GOAL_NET.md)。

## 文档索引

| 文档 | 内容 |
|------|------|
| [FOOTBALL_PHYSICS.md](./FOOTBALL_PHYSICS.md) | 球体物理、踢球/带球/观察四周、守门员鱼跃、滑铲、加速疾跑、视野外带球指示、相关配置 |
| [FOOTBALL_MATCH.md](./FOOTBALL_MATCH.md) | 比赛阶段、计时、开球、进球/出界、体力机制与比赛 HUD |
| [GOAL_NET.md](./GOAL_NET.md) | 球门网实体与渲染 |

## 配置与调试

- 服务端：`config/nmbct-football-server.json`（`/football config` 或 YACL）
- 客户端：`config/nmbct-football-client.json`
- 赛场：`config/nmbct-football-match.json`（`/match` 与赛场 GUI）

## 场员默认按键（可改键）

| 按键 | 功能 |
|------|------|
| 左键按住 | 带球 |
| `R` | 踢球 / 守门员鱼跃蓄力 |
| `X` | 停球 / 守门员接球 |
| `V` | 挑球 |
| `C` | 冲刺时滑铲 |
| `Z` | 加速疾跑（客户端可设切换或按住，默认按住） |
| 左 `Alt` 按住 | 观察四周（移动仍按按下时的朝向） |

按键提示 HUD 在聊天栏关闭时仍会显示常用操作（含滑铲、加速疾跑、鱼跃等）。

## 规则说明（概要）

- **比赛**：由管理员通过 `/match` 开赛；自动判定进球、边线/底线出界、开球锁定与补时；详见 [FOOTBALL_MATCH.md](./FOOTBALL_MATCH.md)。
- **体力**：疾跑与跳跃持续消耗，停止消耗后延迟回复；低体力降低移速；滑铲、鱼跃蓄力、加速疾跑等额外扣体或倍率见配置 `stamina_mechanism`。
- **对抗**：滑铲可撞开对方；球与球员有推球/撞人反馈；带球时与所带之球无碰撞，结束后短暂 grace。
- **守门员**：未持球时可鱼跃（`R` 蓄力）、接球、击球；鱼跃扑救使用三维锥体判定（含近身与高球放宽）。

更细的数值默认与调参说明以各专题文档为准。
