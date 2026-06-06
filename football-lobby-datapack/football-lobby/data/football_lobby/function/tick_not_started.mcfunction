execute as @a[team=!football_A] at @s \
    if entity @e[type=marker,tag=football_lobby_entity,tag=football_lobby_team_a,distance=..3] \
    if block ~ ~-0.1 ~ red_concrete \
    run function football_lobby:join_team_a
execute as @a[team=!football_B] at @s \
    if entity @e[type=marker,tag=football_lobby_entity,tag=football_lobby_team_b,distance=..3] \
    if block ~ ~-0.1 ~ blue_concrete \
    run function football_lobby:join_team_b
execute as @a[team=!spec] at @s \
    if entity @e[type=marker,tag=football_lobby_entity,tag=football_lobby_team_spec,distance=..3] \
    if block ~ ~-0.1 ~ light_gray_concrete \
    run function football_lobby:join_team_spec
