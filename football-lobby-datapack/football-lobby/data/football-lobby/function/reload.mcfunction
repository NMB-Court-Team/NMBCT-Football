kill @e[tag=football_lobby_entity]
tag @a remove football_lobby_in_a
tag @a remove football_lobby_in_b
tag @a remove football_lobby_in_spec
tag @a remove football_lobby_detected_a
tag @a remove football_lobby_detected_b
tag @a remove football_lobby_detected_spec
data modify storage football-lobby:runtime team_a_x set string storage football-lobby:points team_a[0] 0 -1
data modify storage football-lobby:runtime team_a_y set string storage football-lobby:points team_a[1] 0 -1
data modify storage football-lobby:runtime team_a_z set string storage football-lobby:points team_a[2] 0 -1
data modify storage football-lobby:runtime team_b_x set string storage football-lobby:points team_b[0] 0 -1
data modify storage football-lobby:runtime team_b_y set string storage football-lobby:points team_b[1] 0 -1
data modify storage football-lobby:runtime team_b_z set string storage football-lobby:points team_b[2] 0 -1
data modify storage football-lobby:runtime team_spec_x set string storage football-lobby:points team_spec[0] 0 -1
data modify storage football-lobby:runtime team_spec_y set string storage football-lobby:points team_spec[1] 0 -1
data modify storage football-lobby:runtime team_spec_z set string storage football-lobby:points team_spec[2] 0 -1
data modify storage football-lobby:runtime start_x set string storage football-lobby:points start_button[0] 0 -1
data modify storage football-lobby:runtime start_y set string storage football-lobby:points start_button[1] 0 -1
data modify storage football-lobby:runtime start_z set string storage football-lobby:points start_button[2] 0 -1
data modify storage football-lobby:runtime settings_x set string storage football-lobby:points settings_button[0] 0 -1
data modify storage football-lobby:runtime settings_y set string storage football-lobby:points settings_button[1] 0 -1
data modify storage football-lobby:runtime settings_z set string storage football-lobby:points settings_button[2] 0 -1
function football-lobby:spawn with storage football-lobby:runtime
execute if entity @e[type=marker,tag=football_lobby_team_a,limit=1] if entity @e[type=marker,tag=football_lobby_team_b,limit=1] if entity @e[type=marker,tag=football_lobby_team_spec,limit=1] if entity @e[type=marker,tag=football_lobby_start_anchor,limit=1] if entity @e[type=marker,tag=football_lobby_settings_anchor,limit=1] run tellraw @a [{"text":"[football-lobby] ","color":"gold"},{"text":"大厅已加载","color":"green"}]
execute unless entity @e[type=marker,tag=football_lobby_team_a,limit=1] run tellraw @a [{"text":"[football-lobby] ","color":"red"},{"text":"红队点位生成失败：目标区块未加载。","color":"red"}]
execute unless entity @e[type=marker,tag=football_lobby_team_b,limit=1] run tellraw @a [{"text":"[football-lobby] ","color":"red"},{"text":"蓝队点位生成失败：目标区块未加载。","color":"red"}]
execute unless entity @e[type=marker,tag=football_lobby_team_spec,limit=1] run tellraw @a [{"text":"[football-lobby] ","color":"red"},{"text":"旁观者点位生成失败：目标区块未加载。","color":"red"}]
execute unless entity @e[type=marker,tag=football_lobby_start_anchor,limit=1] run tellraw @a [{"text":"[football-lobby] ","color":"red"},{"text":"开始按钮生成失败：目标区块未加载。","color":"red"}]
execute unless entity @e[type=marker,tag=football_lobby_settings_anchor,limit=1] run tellraw @a [{"text":"[football-lobby] ","color":"red"},{"text":"设置按钮生成失败：目标区块未加载。","color":"red"}]
