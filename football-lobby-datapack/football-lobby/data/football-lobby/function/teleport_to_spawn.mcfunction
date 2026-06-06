data modify storage football-lobby:runtime x set string storage football-lobby:points spawn_point[0] 0 -1
data modify storage football-lobby:runtime y set string storage football-lobby:points spawn_point[1] 0 -1
data modify storage football-lobby:runtime z set string storage football-lobby:points spawn_point[2] 0 -1
function football-lobby:return_to_spawn with storage football-lobby:runtime
