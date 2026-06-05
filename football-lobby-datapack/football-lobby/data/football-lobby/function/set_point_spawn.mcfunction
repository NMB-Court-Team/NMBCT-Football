execute store result storage football-lobby:tmp x double 1 run data get entity @s Pos[0]
execute store result storage football-lobby:tmp y double 1 run data get entity @s Pos[1]
execute store result storage football-lobby:tmp z double 1 run data get entity @s Pos[2]
tellraw @s [{"text":"[football-lobby] ","color":"gold"},{"text":"重生点已设置","color":"green"}]
