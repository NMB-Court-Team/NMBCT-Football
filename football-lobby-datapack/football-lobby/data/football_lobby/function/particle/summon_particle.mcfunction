# @param end_x: double, end_y: double, end_z: double  trail粒子结束点坐标
# @param particle_color: int  trail粒子颜色(AARRGGBB.toInt())

$execute at 0-0-0-0-1 run particle trail{color:$(particle_color),duration:20,target:[$(end_x),$(end_y),$(end_z)]} ~ ~ ~ 0 0 0 0 1

kill 0-0-0-0-1
# 0-0-0-0-1's lifecycle starts in x/z_generate_particle_pos.mcfunction
