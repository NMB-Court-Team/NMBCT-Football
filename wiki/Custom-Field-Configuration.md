# Custom Field Configuration

Language: **English** | [中文](Custom-Field-Configuration-zh-CN)

This page explains how to connect your own Minecraft football field to the NMBCT Football match system. Field data is saved in `nmbct-football-match.json` and can also be edited through the in-game UI.

## Recommended Method

Use the in-game field editor whenever possible:

1. Join the server with OP or GM permission.
2. Stand at the positions you want to sample.
3. Run `/match config`.
4. Fill in goals, sidelines, kickoff point, corner kicks, goal kicks, penalty spots, penalty areas, and team spawn points.
5. Save the screen. The active `nmbct-football-match.json` will be updated.

Manual JSON editing also works:

- Global config path: `<Fabric config directory>/nmbct-football-match.json`
- World-specific path: `<world>/nmbct-football-match.json`
- If the world-specific file exists, it takes priority when the server starts.

## File Priority

The match config is loaded in this order:

1. `<world>/nmbct-football-match.json`
2. `<Fabric config directory>/nmbct-football-match.json`

Use the world file when a field belongs to a specific map and should travel with that save.

## Coordinate Rules

- `x`, `y`, and `z` are Minecraft world coordinates and may be decimals.
- `yaw` and `pitch` are only used by spawn positions and may be omitted.
- `y` is usually the field ground height.
- Keep `field_config_version` as `2` for new configs.

The playable rectangle is defined by:

- `goal_a` and `goal_b` as the two goal lines.
- `sideline_a` and `sideline_b` as the two sidelines.
- `kick_off` as the center spot and midfield reference.

## Minimal Structure

Start from the repository sample when possible. The shape below shows the main sections:

```json
{
  "field_config_version": 2,
  "team_a_name": "Red",
  "team_b_name": "Blue",
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

`goal_a`, `goal_b`, `sideline_a`, and `sideline_b` must be filled before the field is usable.

## Goals

Each goal uses this structure:

| Field | Meaning |
| --- | --- |
| `x1`, `y1`, `z1` | First corner of the goal rectangle |
| `x2`, `y2`, `z2` | Opposite corner of the goal rectangle |
| `facing_x`, `facing_y`, `facing_z` | Goal facing direction, used for goal-line logic |
| `goal_kick` | Goal kick placement point |
| `corner_kick_left` | Left corner kick point |
| `corner_kick_right` | Right corner kick point |
| `penalty_spot` | Penalty spot; optional but recommended for custom fields |
| `half_area` | Goal area, penalty area, and penalty arc for this half |

Example:

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

For custom-scale fields, explicitly set `penalty_spot` for both goals.

## Penalty Areas

`half_area` defines the boxes around one goal:

| Field | Meaning |
| --- | --- |
| `goal_area_corner1`, `goal_area_corner2` | Two opposite corners of the goal area |
| `penalty_area_corner1`, `penalty_area_corner2` | Two opposite corners of the penalty area |
| `penalty_arc_radius` | Penalty arc radius, default `10.0` |

```json
"half_area": {
  "goal_area_corner1": { "x": -1.5, "y": -60.0, "z": 66.5 },
  "goal_area_corner2": { "x": 18.5, "y": -60.0, "z": 81.5 },
  "penalty_area_corner1": { "x": -11.5, "y": -60.0, "z": 55.5 },
  "penalty_area_corner2": { "x": 28.5, "y": -60.0, "z": 81.5 },
  "penalty_arc_radius": 10.0
}
```

## Sidelines

`sideline_a` and `sideline_b` use the same structure:

| Field | Meaning |
| --- | --- |
| `axis` | Line extension axis: `"x"` means the line runs along X; `"z"` means it runs along Z |
| `coord` | Fixed coordinate: fixed Z for `axis = "x"`, fixed X for `axis = "z"` |
| `positive_inside` | Whether the positive side of the fixed coordinate axis is inside the field |

Example: a sideline running along Z, fixed at `x = 43.2`, with the inside on negative X:

```json
"sideline_a": {
  "coord": 43.2,
  "axis": "z",
  "positive_inside": false
}
```

## Kickoff and Set-Piece Radii

| Field | Default | Meaning |
| --- | --- | --- |
| `kick_off` | `{ "x": 8.5, "y": -60.0, "z": 8.5 }` | Center kickoff spot |
| `center_circle_radius` | `10.0` | Center circle restriction radius |
| `corner_kick_penalty_area_radius` | `10.0` | Required distance around corner kicks |
| `throw_in_penalty_area_radius` | `2.5` | Required distance around throw-ins |
| `free_kick_distance_radius` | `10.0` | Required distance around free kicks |

These values affect automatic set-piece restrictions and player repositioning.

## Spawn Points

Each team has one goalkeeper spawn and a list of player spawns:

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

Tips:

- Put `gk` in front of that team's goal.
- Add enough `players` entries for the maximum number of outfield players.
- Use `yaw` to face players toward the attacking direction.

## Testing

Quick test commands:

```mcfunction
/match join A
/match join B
/match start
```

Useful admin commands:

| Command | Purpose |
| --- | --- |
| `/match setup` | Open match rules and accessibility config |
| `/match config` | Open field geometry config |
| `/match reset` | Reset to pre-match |
| `/match pause` | Pause or resume the timer |
| `/match scoreA <value>` | Set Team A score |
| `/match scoreB <value>` | Set Team B score |
| `/match setGk A <player>` | Set Team A goalkeeper |
| `/match setGk B <player>` | Set Team B goalkeeper |

## Troubleshooting

| Problem | Check |
| --- | --- |
| Goals are not detected | Goal rectangle corners and `facing_x/y/z` |
| Out-of-bounds is reversed | `sideline_a/b.positive_inside` |
| Players are repositioned oddly | `kick_off`, `center_circle_radius`, `half_area`, and set-piece radii |
| Penalty kicks use the wrong spot | Explicitly set `goal_a.penalty_spot` and `goal_b.penalty_spot` |
| Global config changes do not apply | Check whether the world folder has its own `nmbct-football-match.json` |
