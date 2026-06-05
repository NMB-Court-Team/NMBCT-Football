data modify storage football-lobby:points start_button set from entity @s Pos
data modify storage football-lobby:points start_button_rotation set from entity @s Rotation
data modify storage football-lobby:points start_button_rotation[1] set value 0.0f
tellraw @s [{"text":"[football-lobby] ","color":"gold"},{"text":"开始按钮位置已设置。","color":"green"}]
