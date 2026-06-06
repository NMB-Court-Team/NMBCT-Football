scoreboard players set #is_prev_not_start football_lobby.temp 0
execute if score #tick.match_stage.prev football_lobby.system = #PRE_MATCH football_lobby.stage run scoreboard players set #is_prev_not_start football_lobby.temp 1
execute if score #tick.match_stage.prev football_lobby.system = #FINISHED football_lobby.stage run scoreboard players set #is_prev_not_start football_lobby.temp 1

scoreboard players set #is_curr_not_start football_lobby.temp 0
execute if score #tick.match_stage.curr football_lobby.system = #PRE_MATCH football_lobby.stage run scoreboard players set #is_curr_not_start football_lobby.temp 1
execute if score #tick.match_stage.curr football_lobby.system = #FINISHED football_lobby.stage run scoreboard players set #is_curr_not_start football_lobby.temp 1

execute if score #is_prev_not_start football_lobby.temp matches 1 if score #is_curr_not_start football_lobby.temp matches 0 run function football_lobby:on_finished
execute if score #is_prev_not_start football_lobby.temp matches 0 if score #is_curr_not_start football_lobby.temp matches 1 run function football_lobby:on_start

execute unless score #tick.match_stage.prev football_lobby.system = #PRE_MATCH football_lobby.stage if score #tick.match_stage.curr football_lobby.system = #PRE_MATCH football_lobby.stage run function football_lobby:on_enter_pre_match
