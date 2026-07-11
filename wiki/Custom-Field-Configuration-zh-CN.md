# 自定义球场配置

语言：[English](Custom-Field-Configuration) | **中文**

本页说明如何把自己建好的 Minecraft 球场接入 NMBCT Football 的正式比赛系统。球场配置由 `nmbct-football-match.json` 保存，也可以通过游戏内 GUI 编辑。

## 推荐方式

优先使用游戏内 GUI：

1. 以 OP 或 GM 权限进入服务器。
2. 站到需要采样的位置。
3. 执行 `/match config`。
4. 填写球门、边线、开球点、角球点、门球点、点球点、禁区和双方出生点。
5. 保存后，当前生效的 `nmbct-football-match.json` 会被更新。

也可以手动编辑 JSON：

- 全局路径：`<Fabric 配置目录>/nmbct-football-match.json`
- 世界专用路径：`<world>/nmbct-football-match.json`
- 如果世界目录存在同名文件，服务器启动时会优先使用世界内配置。

## 文件优先级

服务器按下面顺序读取赛场配置：

1. `<world>/nmbct-football-match.json`
2. `<Fabric 配置目录>/nmbct-football-match.json`

如果某个球场绑定在特定地图里，推荐把配置放进世界根目录，方便随存档迁移。

## 坐标约定

- `x`、`y`、`z` 是 Minecraft 世界坐标，可以使用小数。
- `yaw`、`pitch` 只用于出生点朝向，可省略。
- `y` 通常填球场地面高度。
- 新配置建议保持 `field_config_version` 为 `2`。

球场矩形由以下内容定义：

- `goal_a` 和 `goal_b`：两条底线和球门。
- `sideline_a` 和 `sideline_b`：两条边线。
- `kick_off`：中圈开球点和中线参考。

## 关键字段

| 字段 | 含义 |
| --- | --- |
| `goal_a`, `goal_b` | 两座球门、门球点、角球点、点球点和半场禁区 |
| `sideline_a`, `sideline_b` | 边线方向和场内方向 |
| `kick_off` | 开球点 |
| `center_circle_radius` | 中圈限制半径 |
| `corner_kick_penalty_area_radius` | 角球限制半径 |
| `throw_in_penalty_area_radius` | 界外球限制半径 |
| `free_kick_distance_radius` | 任意球防守距离 |
| `team_a_spawn`, `team_b_spawn` | 双方守门员和普通球员出生点 |

## 球门

每座球门包含：

| 字段 | 含义 |
| --- | --- |
| `x1`, `y1`, `z1` | 球门矩形第一个角 |
| `x2`, `y2`, `z2` | 球门矩形对角 |
| `facing_x`, `facing_y`, `facing_z` | 球门朝向，用于进球和底线逻辑 |
| `goal_kick` | 门球摆球点 |
| `corner_kick_left`, `corner_kick_right` | 两侧角球点 |
| `penalty_spot` | 点球点；自定义比例球场建议显式填写 |
| `half_area` | 小禁区、大禁区和点球弧 |

`goal_a` 和 `goal_b` 的朝向通常相反。进球不判定时，优先检查球门矩形和 `facing_*`。

## 边线

`sideline_a` 和 `sideline_b` 使用同样结构：

| 字段 | 含义 |
| --- | --- |
| `axis` | 边线延伸方向，`"x"` 表示沿 X，`"z"` 表示沿 Z |
| `coord` | 固定坐标；`axis = "x"` 时是固定 Z，`axis = "z"` 时是固定 X |
| `positive_inside` | 固定坐标轴正方向是否为场内 |

如果出界方向反了，通常就是 `positive_inside` 填反了。

## 出生点

每队有一个 `gk` 和一个 `players` 列表：

```json
"team_a_spawn": {
  "gk": {
    "x": 8.5,
    "y": -60.0,
    "z": 77.5,
    "yaw": -180.0
  },
  "players": [
    { "x": -1.5, "y": -60.0, "z": 66.5, "yaw": -180.0 },
    { "x": 8.5, "y": -60.0, "z": 66.5, "yaw": -180.0 }
  ]
}
```

建议给最大参赛人数准备足够出生点，并让 `yaw` 面向进攻方向。

## 测试命令

```mcfunction
/match join A
/match join B
/match start
```

常用管理命令：

| 命令 | 用途 |
| --- | --- |
| `/match setup` | 打开比赛规则和辅助功能配置 |
| `/match config` | 打开球场几何配置 |
| `/match reset` | 重置到赛前 |
| `/match pause` | 暂停或恢复计时 |
| `/match scoreA <value>` | 设置 A 队比分 |
| `/match scoreB <value>` | 设置 B 队比分 |
| `/match setGk A <player>` | 指定 A 队守门员 |
| `/match setGk B <player>` | 指定 B 队守门员 |
