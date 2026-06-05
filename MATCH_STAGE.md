# `/match stage` 比赛阶段码

`/match stage` 返回当前比赛阶段对应的**整数码**，供数据包、函数与命令方块通过 `execute store result score ... run match stage` 读取。

- **命令**：`/match stage`
- **返回值**：`MatchPhase` 枚举的 `ordinal`（从 0 起）
- **权限**：无额外要求（与 `/match join` 相同，普通玩家与函数均可执行）
- **输出**：不向聊天栏发送消息，仅写入命令执行结果

## 返回值对照表

| 码 | 枚举名 | 含义 |
|----|--------|------|
| 0 | `PRE_MATCH` | 未开始（赛前） |
| 1 | `FIRST_HALF` | 上半场 |
| 2 | `FIRST_HALF_ET` | 上半场补时 |
| 3 | `SECOND_HALF` | 下半场 |
| 4 | `SECOND_HALF_ET` | 下半场补时 |
| 5 | `EXTRA_FIRST` | 加时上半场 |
| 6 | `EXTRA_FIRST_ET` | 加时上半场补时 |
| 7 | `EXTRA_SECOND` | 加时下半场 |
| 8 | `EXTRA_SECOND_ET` | 加时下半场补时 |
| 9 | `PENALTIES` | 点球大战 |
| 10 | `PRE_MATCH_PREP` | 赛前准备 |
| 11 | `FINISHED` | 结算（比赛结束） |

> 阶段码与网络同步包 `MatchPhase.STREAM_CODEC` 使用的整数值一致。若未来在枚举中间插入新阶段，后续码位会整体后移，数据包逻辑应依赖本文档或源码中的 `MatchPhase` 定义，而非硬编码假设顺序。

## 数据包示例

将当前阶段写入记分板 `stage`  objective 中玩家 `@s` 的分数：

```mcfunction
execute store result score @s stage run match stage
```

判断当前是否处于上半场（码 = 1）：

```mcfunction
execute store result score #tmp stage run match stage
execute if score #tmp stage matches 1 run ...
```

## 相关命令

| 命令 | 说明 |
|------|------|
| `/match phase` | 向执行者显示当前阶段名称与剩余时间（需 GM） |
| `/match phase set <PHASE>` | 手动设置阶段（需 GM） |
| `/match phase advance` | 推进到下一阶段（需 GM） |

比赛流程与阶段语义详见 [FOOTBALL_MATCH.md](./FOOTBALL_MATCH.md)。
