tag @a remove football_lobby_detected_a
tag @a remove football_lobby_detected_b
tag @a remove football_lobby_detected_spec
execute at @e[type=marker,tag=football_lobby_team_a,limit=1] positioned ~-1.5 ~-0.5 ~-1.5 run function football-lobby:area_a
execute at @e[type=marker,tag=football_lobby_team_b,limit=1] positioned ~-1.5 ~-0.5 ~-1.5 run function football-lobby:area_b
execute at @e[type=marker,tag=football_lobby_team_spec,limit=1] positioned ~-1.5 ~-0.5 ~-1.5 run function football-lobby:area_spec
execute store result score #particle_count football_lobby run data get storage football-lobby:config particles_per_team_per_tick 1
execute if score #particle_count football_lobby matches 65.. run scoreboard players set #particle_count football_lobby 64
execute if score #particle_count football_lobby matches 1..64 run function football-lobby:particles/loop
tag @a[tag=football_lobby_in_a,tag=!football_lobby_detected_a] remove football_lobby_in_a
tag @a[tag=football_lobby_in_b,tag=!football_lobby_detected_b] remove football_lobby_in_b
tag @a[tag=football_lobby_in_spec,tag=!football_lobby_detected_spec] remove football_lobby_in_spec
execute as @e[type=interaction,tag=football_lobby_start] if data entity @s interaction run function football-lobby:click_start
execute as @e[type=interaction,tag=football_lobby_settings] if data entity @s interaction run function football-lobby:click_settings
