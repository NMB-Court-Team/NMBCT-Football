execute store result score #stage football_lobby run match stage
execute if score #stage football_lobby matches 0 if data storage football-lobby:points spawn_point run function football-lobby:teleport_to_spawn
