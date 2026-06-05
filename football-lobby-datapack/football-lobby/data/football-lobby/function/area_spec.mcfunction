tag @a[dx=2,dy=1,dz=2] add football_lobby_detected_spec
execute as @a[dx=2,dy=1,dz=2,tag=!football_lobby_in_spec] run function football-lobby:join_spec
tag @a[dx=2,dy=1,dz=2] add football_lobby_in_spec
