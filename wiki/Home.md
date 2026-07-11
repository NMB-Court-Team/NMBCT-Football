# NMBCT Football Wiki

Language: **English** | [中文](Home-zh-CN)

This wiki explains how to configure NMBCT Football for custom Minecraft football fields, match rules, server-side gameplay tuning, and client-side display options.

## Pages

| Page | What it covers |
| --- | --- |
| [Custom Field Configuration](Custom-Field-Configuration) | Build and configure your own field, goals, sidelines, kickoff point, set-piece spots, penalty areas, and spawn points |
| [Configuration Guide](Configuration-Guide) | Overview of `nmbct-football-match.json`, `nmbct-football-server.json`, and `nmbct-football-client.json` |

## Quick Commands

- Match setup UI: `/match setup`
- Field geometry UI: `/match config`
- Server gameplay config UI: `/football config`
- Summon a football: `/football summon`
- Join a team: `/match join A` or `/match join B`
- Start a match: `/match start`

## Configuration Files

| File | Purpose |
| --- | --- |
| `config/nmbct-football-match.json` | Match rules, team names, field geometry, spawn points, accessibility |
| `config/nmbct-football-server.json` | Server-authoritative physics, kicking, dribbling, goalkeeper, stamina, particles |
| `config/nmbct-football-client.json` | Local client HUD, rendering, input preferences |

## Recommended Setup Flow

1. Build the field and goals in your Minecraft world.
2. Run `/match config` and enter the field geometry.
3. Run `/match setup` and set teams, duration, extra time, penalties, offside, and accessibility options.
4. Run `/football config` if you want to tune kicking, dribbling, goalkeeper behavior, stamina, particles, or physics.
5. Test with `/match join A`, `/match join B`, and `/match start`.
