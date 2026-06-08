scoreboard objectives remove football_lobby.temp
scoreboard objectives remove football_lobby.leave_server_count


scoreboard objectives add football_lobby.system dummy "系统变量"
scoreboard objectives add football_lobby.temp dummy "临时变量"
scoreboard objectives add football_lobby.stage dummy "比赛阶段常数"
scoreboard objectives add football_lobby.const dummy "常数"
scoreboard objectives add football_lobby.trigger trigger "比赛大厅触发器"
scoreboard objectives add football_lobby.leave_server_count minecraft.custom:minecraft.leave_game "退出服务器计数器"

scoreboard players set #PRE_MATCH football_lobby.stage 0
scoreboard players set #FINISHED football_lobby.stage 11

scoreboard players set #2 football_lobby.const 2

scoreboard players set #tick.match_stage.prev football_lobby.system 0
