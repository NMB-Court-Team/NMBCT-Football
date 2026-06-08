execute as @a if score @s football_lobby.leave_server_count matches 1.. run function football_lobby:on_join_server

execute store result score #tick.match_stage.curr football_lobby.system run match stage

execute if score #tick.match_stage.curr football_lobby.system = #PRE_MATCH football_lobby.stage run function football_lobby:tick_not_started
execute if score #tick.match_stage.curr football_lobby.system = #FINISHED football_lobby.stage run function football_lobby:tick_not_started

function football_lobby:check_stage_change

execute as @a unless score @s football_lobby.trigger matches 0 run function football_lobby:on_trigger

scoreboard players operation #tick.match_stage.prev football_lobby.system = #tick.match_stage.curr football_lobby.system

function football_lobby:particle/tick
