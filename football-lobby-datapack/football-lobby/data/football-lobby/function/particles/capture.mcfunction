data modify storage football-lobby:particle x set from entity @s Pos[0]
data modify storage football-lobby:particle y set from entity @s Pos[1]
data modify storage football-lobby:particle z set from entity @s Pos[2]
execute store result storage football-lobby:particle target_offset double 0.0625 run random value 32..64
function football-lobby:particles/position_target with storage football-lobby:particle
kill @s
