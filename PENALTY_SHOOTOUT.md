# 点球大战

本文档说明 NMBCT Football 模组中**点球大战**的规则背景、MVP 实现范围与后续待办。正赛流程见 [FOOTBALL_MATCH.md](./FOOTBALL_MATCH.md)。

## 规则背景（IFAB Law 10 摘要）

在淘汰赛正赛（含可选加时）结束后仍**平局**时，通过**点球大战**决出胜负：

1. 掷币选择罚球所用球门，再掷币选择先踢或后踢。
2. 双方按 **ABAB** 交替罚球，每队最多 **5** 次；不同球员各踢一次（正式规则要求全员踢过一轮前不得同一人二踢）。
3. 若一方在对方踢完剩余次数后仍无法追平，**提前结束**。
4. 五轮后仍平则进入**突然死亡**：每轮双方各踢 1 次，一方进一方不进则结束。
5. 点球进球**不计入**正赛球员/球队进球统计。

参考：[IFAB Law 10](https://www.theifab.com/laws/latest/determining-the-outcome-of-a-match/)

## MVP 已实现

- [x] 正赛平局且 `enable_penalty_shootout` 时自动进入 `MatchPhase.PENALTIES`
- [x] 独立点球比分（`penaltyScoreA` / `penaltyScoreB`），**不修改** `teamAScore` / `teamBScore`
- [x] 5 轮 **ABAB** 交替；掷币随机选球门端与先踢方
- [x] **全员至少各踢一次方可二踢**（以点球大战开始时在线的 roster 队员为 eligible 池）
- [x] **提前结束**与**突然死亡**
- [x] 物理射门 + 门将扑救（复用现有踢球/守门员输入）
- [x] 点球阶段隔离正赛判例（不触发 `onGoal`、出界复位、边线判例）
- [x] 每回合摆球至点球点、主罚/门将站位、其余队员锁定
- [x] 赛场配置 `penalty_spot`（`/match config` 球门 Tab）；未配置时沿门线 **11 格**推导
- [x] 网络同步：`PenaltyShootoutSyncS2CPayload`、`PenaltyKickStartS2CPayload`
- [x] HUD：主栏点球副比分、单次开踢 Banner、终场「点球获胜/失利」
- [x] 胜负后自动 `FINISHED` 并广播扩展 `MatchResultS2CPayload`
- [x] `/match phase set PENALTIES` 仅在正赛平局时允许

### 主要源码

| 路径 | 职责 |
|------|------|
| `match/PenaltyShootoutState.kt` | 点球状态机、摆位、胜负判定 |
| `match/GoalConfigExtensions.kt` | 点球点推导 |
| `Football.kt` | `detectPenaltyKick`、`recordActiveKick` 触球 |
| `network/PenaltyShootoutSyncS2CPayload.kt` | 状态同步 |
| `network/PenaltyKickStartS2CPayload.kt` | 开踢 Banner |
| `client/match/PenaltyShootoutClient.kt` | 客户端状态镜像 |
| `client/render/PenaltyKickHudElement.kt` | 开踢 HUD |

## 待实现（Backlog）

- [ ] 主罚顺序由队长/GM 指定（当前：未踢过的 eligible 中非门将优先随机）
- [x] 主罚门将时临时切换为场外操作；人数足够时优先非门将主罚
- [ ] 被罚下球员剔除（依赖红牌/离场系统）
- [ ] 双方人数不均时的弃踢员规则（IFAB）
- [ ] GM 掷币命令（`/match penalty toss`）与可选换门
- [ ] ABBA 顺序选项
- [ ] 点球过程专用音效/哨声序列
- [ ] 多赛场拆分 `MatchState`（全局单场比赛限制仍适用）

## 测试清单

1. `/match setup` 开启点球大战，关闭加时，缩短半场；正赛踢成平局。
2. 自动进入点球阶段：HUD 显示点球 `0-0`，正赛比分不变。
3. 主罚进球：仅点球比分 +1。
4. 踢飞或扑出：轮换对方主罚。
5. 大比分领先时提前结束（如 3-0 后无需踢满 5 轮）。
6. 5-5 后突然死亡：一方进一方不进 → 终场 HUD 显示点球胜负。
7. `/match reset` 清空点球状态。
8. `/match config` 调整 `penalty_spot` 后摆球位置正确。
