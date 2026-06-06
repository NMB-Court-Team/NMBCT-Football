execute positioned 325.5 197.0 325.5 unless entity @a[distance=..64] run return fail
# do not show particle if no one is around

data modify storage football_lobby:particle particle_color set value 0xFF8F2122
execute at @e[type=marker,tag=football_lobby_entity,tag=football_lobby_team_a] run function football_lobby:particle/main

data modify storage football_lobby:particle particle_color set value 0xFF2C2E8E
execute at @e[type=marker,tag=football_lobby_entity,tag=football_lobby_team_b] run function football_lobby:particle/main

data modify storage football_lobby:particle particle_color set value 0xFF7C7C7C
execute at @e[type=marker,tag=football_lobby_entity,tag=football_lobby_team_spec] run function football_lobby:particle/main
