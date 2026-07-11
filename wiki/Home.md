# NMBCT Football Wiki

这里是 NMBCT Football 的用户配置文档。推荐先从自定义球场开始，再根据需要调整比赛规则、服务端手感和客户端显示。

## 页面索引

| 页面 | 内容 |
| --- | --- |
| [自定义球场配置](Custom-Field-Configuration) | 配置自己的球场、球门、边线、开球点、角球点、门球点、禁区、出生点 |
| [配置总览](Configuration-Guide) | `nmbct-football-match.json`、`nmbct-football-server.json`、`nmbct-football-client.json` 的用途和常用字段 |

## 快速入口

- 比赛配置界面：OP 在游戏内执行 `/match setup`
- 球场几何界面：OP 在游戏内执行 `/match config`
- 服务端手感配置：OP 在游戏内执行 `/football config`
- 赛场配置文件：`config/nmbct-football-match.json`
- 服务端配置文件：`config/nmbct-football-server.json`
- 客户端配置文件：`config/nmbct-football-client.json`

## 推荐配置顺序

1. 先建好 Minecraft 世界里的球场和球门。
2. 用 `/match config` 录入球场几何。
3. 用 `/match setup` 设置队名、时间、补时、加时、点球、辅助功能。
4. 用 `/football config` 调整射门、带球、守门员、体力、粒子和物理手感。
5. 开一局测试：`/match join A`、`/match join B`、`/match start`。

