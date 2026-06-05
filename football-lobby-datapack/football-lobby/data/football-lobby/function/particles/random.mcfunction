execute store result storage football-lobby:particle edge int 1 run random value 0..3
execute store result storage football-lobby:particle offset double 0.0625 run random value 0..48
$data modify storage football-lobby:particle team set value "$(team)"
function football-lobby:particles/dispatch_edge with storage football-lobby:particle
