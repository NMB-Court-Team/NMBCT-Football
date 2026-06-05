data modify storage football-lobby:particle team set value "a"
execute at @e[type=marker,tag=football_lobby_team_a,limit=1] run function football-lobby:particles/random with storage football-lobby:particle
data modify storage football-lobby:particle team set value "b"
execute at @e[type=marker,tag=football_lobby_team_b,limit=1] run function football-lobby:particles/random with storage football-lobby:particle
data modify storage football-lobby:particle team set value "spec"
execute at @e[type=marker,tag=football_lobby_team_spec,limit=1] run function football-lobby:particles/random with storage football-lobby:particle
scoreboard players remove #particle_count football_lobby 1
execute if score #particle_count football_lobby matches 1.. run function football-lobby:particles/loop
