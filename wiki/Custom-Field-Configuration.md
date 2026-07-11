# 自定义球场配置

本页说明如何把自己建好的 Minecraft 球场接入 NMBCT Football 的正式比赛系统。球场配置由 `nmbct-football-match.json` 保存，也可以通过游戏内 GUI 编辑。

## 配置方式

推荐使用游戏内 GUI：

1. 以 OP 或 GM 权限进入服务器。
2. 站到你要采样的位置。
3. 执行 `/match config` 打开球场几何配置界面。
4. 按界面分组填写球门、边线、开球点、角球点、门球点、点球点、禁区和双方出生点。
5. 保存后配置会写入当前生效的 `nmbct-football-match.json`。

也可以手动编辑 JSON：

- 全局默认路径：`<Fabric 配置目录>/nmbct-football-match.json`
- 如果世界存档根目录存在 `nmbct-football-match.json`，服务器启动后会优先读取世界内的这个文件。
- 仓库根目录的 `nmbct-football-match.json` 可作为示例模板。

## 配置文件优先级

服务器启动时会按下面顺序决定使用哪个赛场配置：

1. 世界存档根目录：`<world>/nmbct-football-match.json`
2. Fabric 全局配置目录：`config/nmbct-football-match.json`

如果世界目录里有专用配置，它会覆盖全局配置。这样同一台服务器可以为不同世界保存不同球场。

## 坐标约定

所有坐标都是 Minecraft 世界坐标：

- `x`、`y`、`z` 是方块世界坐标，可以使用小数。
- `yaw`、`pitch` 只用于出生点朝向，可省略。
- `y` 一般填球场地面高度。
- `field_config_version` 推荐保持为 `2`。

球场一般由四条边围成：

- 两座球门的门线决定底线方向。
- `sideline_a` 和 `sideline_b` 决定两条边线。
- `kick_off` 是中圈开球点，也用于推导中线。

## 最小字段结构

下面是一个精简结构，实际可以从仓库示例复制后修改：

```json
{
  "field_config_version": 2,
  "team_a_name": "红队",
  "team_b_name": "蓝队",
  "rules": {
    "half_time_minutes": 5,
    "enable_offside": true,
    "enable_stoppage_time": false,
    "stoppage_time_max_minutes": 3,
    "enable_extra_time": false,
    "extra_time_half_minutes": 3,
    "enable_penalty_shootout": false,
    "post_goal_ball_reset_delay_seconds": 3,
    "enable_pre_match_preparation": true,
    "pre_match_preparation_minutes": 2
  },
  "accessibility": {
    "enable_football_position_indicator": false
  },
  "goal_a": {},
  "goal_b": {},
  "sideline_a": {},
  "sideline_b": {},
  "kick_off": { "x": 0.5, "y": 64.0, "z": 0.5 },
  "team_a_spawn": { "gk": {}, "players": [] },
  "team_b_spawn": { "gk": {}, "players": [] }
}
```

手动写 JSON 时，`goal_a`、`goal_b`、`sideline_a`、`sideline_b` 需要补齐下面章节里的字段。空对象只适合说明结构，不适合作为正式球场。

## 球门配置

每座球门使用 `GoalConfig`：

| 字段 | 含义 |
| --- | --- |
| `x1`, `y1`, `z1` | 球门矩形第一个角 |
| `x2`, `y2`, `z2` | 球门矩形对角 |
| `facing_x`, `facing_y`, `facing_z` | 球门朝向，也就是进球方向 |
| `goal_kick` | 门球摆球点 |
| `corner_kick_left` | 左侧角球点 |
| `corner_kick_right` | 右侧角球点 |
| `penalty_spot` | 点球点，可省略；省略时按门线反方向约 11 格推导 |
| `half_area` | 本半场小禁区、大禁区和点球弧 |

`goal_a` 和 `goal_b` 的 `facing_*` 应该指向各自球门内侧或进球穿过门线的方向。两座球门通常朝向相反。

示例：

```json
"goal_a": {
  "x1": 12.8,
  "y1": -60.0,
  "z1": 81.3,
  "x2": 4.2,
  "y2": -56.0,
  "z2": 81.3,
  "facing_x": 0.0,
  "facing_y": 0.0,
  "facing_z": 1.0,
  "goal_kick": { "x": 8.5, "y": -60.0, "z": 80.3 },
  "corner_kick_left": { "x": -26.5, "y": -60.0, "z": 81.5 },
  "corner_kick_right": { "x": 43.5, "y": -60.0, "z": 81.5 },
  "penalty_spot": { "x": 8.5, "y": -60.0, "z": 60.5 }
}
```

## 禁区配置

`half_area` 定义球门所在半场的区域：

| 字段 | 含义 |
| --- | --- |
| `goal_area_corner1` | 小禁区矩形角 1 |
| `goal_area_corner2` | 小禁区矩形角 2 |
| `penalty_area_corner1` | 大禁区矩形角 1 |
| `penalty_area_corner2` | 大禁区矩形角 2 |
| `penalty_arc_radius` | 点球弧半径，默认 `10.0` |

矩形角只需要填两个对角。系统会按水平 `x/z` 范围判断是否在区内。

```json
"half_area": {
  "goal_area_corner1": { "x": -1.5, "y": -60.0, "z": 66.5 },
  "goal_area_corner2": { "x": 18.5, "y": -60.0, "z": 81.5 },
  "penalty_area_corner1": { "x": -11.5, "y": -60.0, "z": 55.5 },
  "penalty_area_corner2": { "x": 28.5, "y": -60.0, "z": 81.5 },
  "penalty_arc_radius": 10.0
}
```

## 边线配置

`sideline_a` 和 `sideline_b` 使用同一种结构：

| 字段 | 含义 |
| --- | --- |
| `axis` | 边线延伸方向，`"x"` 表示边线沿 X 轴延伸，`"z"` 表示边线沿 Z 轴延伸 |
| `coord` | 边线固定坐标；`axis = "x"` 时填固定 Z，`axis = "z"` 时填固定 X |
| `positive_inside` | `true` 表示固定坐标轴正方向一侧是场内，`false` 表示负方向一侧是场内 |

例子：边线沿 Z 轴延伸，固定在 `x = 43.2`，场内在 X 负方向：

```json
"sideline_a": {
  "coord": 43.2,
  "axis": "z",
  "positive_inside": false
}
```

另一条边线固定在 `x = -26.2`，场内在 X 正方向：

```json
"sideline_b": {
  "coord": -26.2,
  "axis": "z",
  "positive_inside": true
}
```

## 开球点和距离半径

| 字段 | 默认值 | 含义 |
| --- | --- | --- |
| `kick_off` | `{ "x": 8.5, "y": -60.0, "z": 8.5 }` | 中圈开球点 |
| `center_circle_radius` | `10.0` | 中圈限制半径 |
| `corner_kick_penalty_area_radius` | `10.0` | 角球时防守方需要退出的半径 |
| `throw_in_penalty_area_radius` | `2.5` | 界外球时其他球员需要退出的半径 |
| `free_kick_distance_radius` | `10.0` | 任意球防守距离 |

这些半径影响定位球时的自动限制和重定位。

## 出生点配置

每队有一个守门员点和若干普通球员点：

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

建议：

- `gk` 放在本方球门前。
- `players` 按你希望的阵型排列。
- `yaw` 面向进攻方向；如果不填，默认 `0`。
- 玩家数多于出生点时，多出来的玩家可能无法得到理想站位，建议按最大参赛人数配置足够点位。

## 游戏内采样建议

配置球场时可以按下面顺序采样：

1. 站到 A 球门左下角和右上角，记录 `goal_a` 的两角。
2. 站到 B 球门两角，记录 `goal_b` 的两角。
3. 站到中点，记录 `kick_off`。
4. 站到两条边线，确认 `axis`、`coord`、`positive_inside`。
5. 站到四个角球点、两个门球点、两个点球点。
6. 站到小禁区和大禁区的对角。
7. 站到双方出生点，保存守门员和球员位置。

## 保存后测试

推荐用下面命令快速测试：

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

## 常见问题

### 进球不判定

检查两座球门的 `x1/y1/z1`、`x2/y2/z2` 是否确实覆盖球门洞口，并确认 `facing_x/y/z` 方向正确。

### 出界方向反了

检查 `sideline_a`、`sideline_b` 的 `positive_inside`。如果球明明在场内却被判出界，通常是场内方向填反了。

### 球员被重定位到奇怪位置

检查 `kick_off`、`center_circle_radius`、`half_area` 和定位球半径。中线、禁区和限制圈都依赖这些字段。

### 点球位置不对

显式填写 `goal_a.penalty_spot` 和 `goal_b.penalty_spot`。不填时系统会根据球门门线和朝向自动推导，适合标准球场，但自定义比例球场可能需要手动写。

### 世界换了以后配置不对

确认当前世界根目录是否存在 `nmbct-football-match.json`。如果存在，服务器会优先使用世界内配置，而不是全局 `config` 目录里的文件。

