# 你在看吗

# 说词啊

# 你看左边，我写了on_trigger, on_finished, on_start

# on_trigger是触发器的统一入口，当然这个数据包只有一种触发器，所以只有一个触发器入口
# on_trigger后分发到各个功能就全都写在这里面

execute if score @s football_lobby.trigger matches 1 run function football_lobby:teleport_to_lobby

scoreboard players set @s football_lobby.trigger 0
