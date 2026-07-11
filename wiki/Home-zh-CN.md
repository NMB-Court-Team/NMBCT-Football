# NMBCT Football Wiki

语言：[English](Home) | **中文**

这里是 NMBCT Football 的用户配置文档，主要说明如何配置自定义 Minecraft 足球场、比赛规则、服务端手感和客户端显示选项。

## 页面索引

| 页面 | 内容 |
| --- | --- |
| [自定义球场配置](Custom-Field-Configuration-zh-CN) | 配置自己的球场、球门、边线、开球点、定位球点、禁区和出生点 |
| [配置总览](Configuration-Guide-zh-CN) | `nmbct-football-match.json`、`nmbct-football-server.json`、`nmbct-football-client.json` 的用途和常用字段 |

## 快速命令

- 比赛配置界面：`/match setup`
- 球场几何界面：`/match config`
- 服务端手感配置：`/football config`
- 生成足球：`/football summon`
- 加入队伍：`/match join A` 或 `/match join B`
- 开始比赛：`/match start`

## 配置文件

| 文件 | 用途 |
| --- | --- |
| `config/nmbct-football-match.json` | 比赛规则、队名、球场几何、出生点、辅助功能 |
| `config/nmbct-football-server.json` | 服务端权威物理、踢球、带球、守门员、体力、粒子 |
| `config/nmbct-football-client.json` | 本地客户端 HUD、渲染和输入偏好 |

## 推荐配置顺序

1. 先在 Minecraft 世界中建好球场和球门。
2. 执行 `/match config` 录入球场几何。
3. 执行 `/match setup` 设置队伍、时长、加时、点球、越位和辅助功能。
4. 如需调手感，执行 `/football config` 调整踢球、带球、守门员、体力、粒子和物理。
5. 用 `/match join A`、`/match join B`、`/match start` 测试一局。
