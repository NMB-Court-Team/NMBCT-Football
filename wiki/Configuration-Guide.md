# Configuration Guide

Language: **English** | [中文](Configuration-Guide-zh-CN)

NMBCT Football has three main configuration files:

| File | Applies to | Purpose |
| --- | --- | --- |
| `nmbct-football-match.json` | Server, with optional world-specific override | Match rules, team names, field geometry, spawn points, accessibility |
| `nmbct-football-server.json` | Server | Authoritative physics, kicking, dribbling, goalkeeper, stamina, particles, slide tackle |
| `nmbct-football-client.json` | Local client | HUD, rendering distance, goal net render mode, boost sprint input mode |

## Saving and Loading

- Missing config files are generated with defaults.
- In-game config screens save back to JSON.
- Invalid JSON falls back to defaults and logs a warning.
- Server configs are synced to online clients, but synced values are not written into local client files.

## Match Config: `nmbct-football-match.json`

In-game entries:

- `/match setup`: teams, match duration, extra time, penalties, offside, accessibility.
- `/match config`: field geometry, goals, sidelines, spawn points.

Load priority:

1. `<world>/nmbct-football-match.json`
2. `<Fabric config directory>/nmbct-football-match.json`

### Top-Level Fields

| Field | Meaning |
| --- | --- |
| `field_config_version` | Field config version; use `2` |
| `team_a_name`, `team_b_name` | Team names |
| `rules` | Match rules |
| `accessibility` | Accessibility options |
| `goal_a`, `goal_b` | Goals and half-field areas |
| `sideline_a`, `sideline_b` | Sidelines |
| `kick_off` | Center kickoff spot |
| `center_circle_radius` | Center circle radius |
| `corner_kick_penalty_area_radius` | Required distance around corner kicks |
| `throw_in_penalty_area_radius` | Required distance around throw-ins |
| `free_kick_distance_radius` | Required distance around free kicks |
| `team_a_spawn`, `team_b_spawn` | Team spawn points |

### `rules`

| Field | Default | Meaning |
| --- | --- | --- |
| `half_time_minutes` | `5` | Minutes per half |
| `enable_offside` | `true` | Enable offside |
| `enable_stoppage_time` | `false` | Enable stoppage time |
| `stoppage_time_max_minutes` | `3` | Maximum stoppage time |
| `enable_extra_time` | `false` | Enable extra time |
| `extra_time_half_minutes` | `3` | Minutes per extra-time half |
| `enable_penalty_shootout` | `false` | Enable penalty shootout if tied |
| `post_goal_ball_reset_delay_seconds` | `3` | Delay before resetting the ball after a goal |
| `enable_pre_match_preparation` | `true` | Enable pre-match preparation phase |
| `pre_match_preparation_minutes` | `2` | Pre-match preparation duration |

Set `half_time_minutes` to `0` with penalty shootout enabled if you want a test match that starts directly with penalties.

### `accessibility`

| Field | Default | Meaning |
| --- | --- | --- |
| `enable_football_position_indicator` | `false` | Show a football position indicator during matches |

This only affects client HUD. It does not change physics, refereeing, or server logic.

## Server Config: `nmbct-football-server.json`

In-game entry: `/football config`

This config is authoritative and affects all players.

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

| Group | Common fields | Meaning |
| --- | --- | --- |
| `core` | `radius`, `mass`, `gravity`, `air_drag`, `ground_friction` | Ball size, gravity, air drag, ground friction |
| `collision` | `restitution`, `wall_restitution`, `wall_spin_retention`, `ground_settle_vy` | Bounce and spin after ground or wall collisions |
| `kick` | `kick_force_scale`, `kick_moving_lateral_damp` | Kick impulse scaling and moving-player correction |

Tuning hints:

- Ball feels too floaty: increase `gravity` or lower `air_drag`.
- Ball rolls too far: lower `ground_friction`.
- Wall bounces are too strong: lower `wall_restitution`.

### `player_input`

| Group | Meaning |
| --- | --- |
| `kick` | Kick range, pass force, min/max shot force, chip settings |
| `dribble` | Dribble target distance, control range, speed matching, correction strength |
| `charge` | Charge timing, perfect window, curve input window |
| `collision` | Ball-player push, recoil, and contact grace |
| `slide` | Slide tackle speed, duration, cooldown, player hit, ball hit force |

Common fields:

| Path | Default | Meaning |
| --- | --- | --- |
| `player_input.kick.player_kick_range` | `2.5` | Player football interaction range |
| `player_input.kick.pass_force` | `1.5` | Tap pass force |
| `player_input.kick.shoot_force_min` | `2.0` | Minimum shot force |
| `player_input.kick.shoot_force_max` | `4.0` | Maximum shot force |
| `player_input.dribble.dribble_target_distance` | `1.2` | Target distance while dribbling |
| `player_input.charge.perfect_charge_force_bonus` | `1.08` | Perfect charge force multiplier |
| `player_input.slide.slide_tackle_cooldown_seconds` | `3.0` | Slide tackle cooldown |
| `player_input.slide.slide_ball_kick_force` | `3.0` | Ball force when slide tackle hits the ball |

### `goalkeeper`

| Group | Meaning |
| --- | --- |
| `catch` | Catch range, max catchable speed, hold position, steal protection |
| `dive.behavior` | Dive duration, cooldown, range, save/catch logic |
| `dive.pitch` | How view pitch affects dive height and forward motion |
| `dive.impulse` | Charged dive launch and sustain impulse |
| `dive.actions` | Punch, short throw, long throw force |

Common fields:

| Path | Default | Meaning |
| --- | --- | --- |
| `goalkeeper.catch.catch_range` | `3.5` | Catch range |
| `goalkeeper.catch.catch_max_speed` | `1.8` | Maximum ball speed that can be caught directly |
| `goalkeeper.catch.hold_steal_protection_ticks` | `200` | Steal protection after goalkeeper catches the ball; 20 ticks is about 1 second |
| `goalkeeper.dive.behavior.dive_range` | `3.0` | Dive interaction range |
| `goalkeeper.dive.behavior.dive_cooldown_ticks` | `24` | Dive cooldown |
| `goalkeeper.dive.actions.throw_long_force_max` | `3.2` | Maximum long throw force |

### `stamina_mechanism`

Stamina affects sprinting, jumping, slide tackling, charged goalkeeper dives, and boost sprint.

| Field | Default | Notes |
| --- | --- | --- |
| `max_stamina` | `1000` | Valid range `50` to `5000` |
| `jump_cost` | `60` | Valid range `0` to `200` |
| `sprint_drain_per_second` | `10` | Valid range `0` to `50` |
| `recovery_delay_seconds` | `1` | Valid range `0.05` to `5` |
| `recovery_per_second` | `20` | Valid range `0` to `100` |
| `half_time_recovery_fraction` | `0.6` | Stamina recovered at half time |
| `goal_recovery_fraction` | `0.15` | Stamina recovered after a goal |
| `speed_tiers` | see below | Low-stamina movement multipliers |

Default speed tiers:

```json
"speed_tiers": [
  { "stamina_fraction": 0.0, "speed_multiplier": 0.6 },
  { "stamina_fraction": 0.1, "speed_multiplier": 0.7 },
  { "stamina_fraction": 0.4, "speed_multiplier": 0.85 },
  { "stamina_fraction": 0.8, "speed_multiplier": 0.95 }
]
```

`stamina_fraction` is an upper threshold. If current stamina ratio is strictly below that value, the matching `speed_multiplier` is used. Tiers must be sorted by `stamina_fraction`, and speed multipliers should not decrease as stamina increases.

### `particles`

| Group | Meaning |
| --- | --- |
| `bounce` | Bounce particle thresholds and spread |
| `counts` | Particle counts for kicks, dribbles, traps, goalkeeper actions |
| `high_speed_drag` | High-speed ball trail particles |
| `kick_visual` | Kick sweep and cloud ring visuals |

If server performance is tight, reduce `particles.counts.*_count` and `high_speed_drag.*_count_*` first.

## Client Config: `nmbct-football-client.json`

Client config only affects local display and input preference.

| Field | Default | Meaning |
| --- | --- | --- |
| `hint_hide_extra_range` | `0.4` | Extra range for hiding key hints |
| `render_stationary_speed_sqr` | `0.0001` | Squared speed threshold for stationary rendering |
| `client_correction_threshold` | `0.25` | Client correction threshold |
| `ball_render_dist` | `128.0` | Maximum football render distance |
| `ball_billboard_ratio` | `0.62` | Distance ratio for switching to billboard rendering |
| `dribble_hold_packet_interval` | `2` | Packet interval while holding dribble |
| `goal_net_render_mode` | `auto` | Goal net render mode |
| `boost_sprint_input_mode` | `hold` | Boost sprint input mode |

`goal_net_render_mode` options:

- `auto`
- `vanilla_compat`
- `shader_compat`

Use Mod Menu or the client config UI for local preferences.

## Troubleshooting

| Symptom | Check first |
| --- | --- |
| Config resets to defaults | JSON syntax and field names |
| Global match config does not apply | Whether the world folder has its own `nmbct-football-match.json` |
| Client display differs between players | Local `nmbct-football-client.json` |
| Gameplay feel is shared by everyone | Server `nmbct-football-server.json`; clients cannot override authoritative gameplay |
| Low stamina speed is wrong | `speed_tiers` ordering and non-decreasing multipliers |
