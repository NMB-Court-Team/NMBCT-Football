# @executor 选队的3x3地块中心的marker

execute positioned ~-1.5 ~ ~1.5 run function football_lobby:particle/x_side
execute positioned ~-1.5 ~ ~-1.5 run function football_lobby:particle/x_side
execute positioned ~1.5 ~ ~-1.5 run function football_lobby:particle/z_side
execute positioned ~-1.5 ~ ~-1.5 run function football_lobby:particle/z_side

# 工作原理：
# 选中一个角落，沿着边向前移动[0, 3]的随机数个单位
# 生成粒子为trail粒子，这个粒子还需要一个结束点参数，结束点为起始点往y+方向移动[2, 4]的随机数个单位
