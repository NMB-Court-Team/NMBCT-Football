# @param horizontal_offset: double  marker horizontal offset, in blocks
# @param vertical_offset: double  marker vertical offset, in blocks

$summon marker ~$(horizontal_offset) ~ ~ {UUID:uuid("0-0-0-0-1")}
$summon marker ~$(horizontal_offset) ~$(vertical_offset) ~ {UUID:uuid("0-0-0-0-2")}

data modify storage football_lobby:particle end_x set from entity 0-0-0-0-2 Pos[0]
data modify storage football_lobby:particle end_y set from entity 0-0-0-0-2 Pos[1]
data modify storage football_lobby:particle end_z set from entity 0-0-0-0-2 Pos[2]

#kill 0-0-0-0-1
kill 0-0-0-0-2

# 0-0-0-0-1's lifecycle ends in summon_particle.mcfunction
