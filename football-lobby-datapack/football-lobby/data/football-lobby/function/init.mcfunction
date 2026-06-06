data modify storage football-lobby:points initialized set value 1b
data modify storage football-lobby:points start_button set value [0.5d,64.0d,0.5d]
data modify storage football-lobby:points start_button_rotation set value [0.0f,0.0f]
data modify storage football-lobby:points settings_button set value [2.5d,64.0d,0.5d]
data modify storage football-lobby:points settings_button_rotation set value [0.0f,0.0f]
data modify storage football-lobby:points team_a set value [-3.5d,64.0d,0.5d]
data modify storage football-lobby:points team_b set value [4.5d,64.0d,0.5d]
data modify storage football-lobby:points team_spec set value [0.5d,64.0d,4.5d]
data modify storage football-lobby:points spawn_point set value [0.5d,64.0d,0.5d]
data modify storage football-lobby:config particles_per_team_per_tick set value 3
# tellraw @a [{"text":"[football-lobby] ","color":"gold"},{"text":"坐标 storage 已初始化。请站在目标位置执行 set_point_* 函数。","color":"yellow"}]
