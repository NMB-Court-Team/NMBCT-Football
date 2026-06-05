$data modify storage football-lobby:config particles_per_team_per_tick set value $(count)
$tellraw @s [{"text":"[football-lobby] ","color":"gold"},{"text":"每队每 tick 粒子次数已设置为 $(count)。","color":"aqua"}]
