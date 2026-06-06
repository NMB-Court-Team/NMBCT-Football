execute store result score #horizontal_offset football_lobby.temp run random value 0..48
execute store result score #vertical_offset football_lobby.temp run random value 32..64

execute store result storage football_lobby:particle horizontal_offset double 0.0625 run scoreboard players get #horizontal_offset football_lobby.temp
execute store result storage football_lobby:particle vertical_offset double 0.0625 run scoreboard players get #vertical_offset football_lobby.temp
