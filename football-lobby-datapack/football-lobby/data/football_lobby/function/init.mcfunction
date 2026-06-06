scoreboard objectives remove football_lobby.temp


scoreboard objectives add football_lobby.system dummy "系统变量"
scoreboard objectives add football_lobby.temp dummy "临时变量"
scoreboard objectives add football_lobby.stage dummy "比赛阶段常数"
scoreboard objectives add football_lobby.trigger trigger "比赛大厅触发器"

scoreboard players set #PRE_MATCH football_lobby.stage 0
scoreboard players set #FINISHED football_lobby.stage 11

scoreboard players set #tick.match_stage.prev football_lobby.system 0
