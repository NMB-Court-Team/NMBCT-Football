data modify storage football-lobby:points settings_button set from entity @s Pos
data modify storage football-lobby:points settings_button_rotation set from entity @s Rotation
data modify storage football-lobby:points settings_button_rotation[1] set value 0.0f
tellraw @s [{"text":"[football-lobby] ","color":"gold"},{"text":"设置按钮位置已设置。","color":"yellow"}]
