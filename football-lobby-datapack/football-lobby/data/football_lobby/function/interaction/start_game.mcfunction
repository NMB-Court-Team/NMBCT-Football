advancement revoke @s only football_lobby:interaction/start_game

scoreboard players set #error_code football_lobby.temp 0
# error code:
# 0 - no error
# 1 - match already started
# 2 - team A has less than 2 players
# 3 - team B has less than 2 players

# check match stage
execute store result score #match_stage football_lobby.temp run match stage
execute unless score #match_stage football_lobby.temp = #PRE_MATCH football_lobby.stage \
    unless score #match_stage football_lobby.temp = #FINISHED football_lobby.stage \
    run scoreboard players set #error_code football_lobby.temp 1

# check team member count
execute if score #error_code football_lobby.temp matches 0 store result score #teamA_count football_lobby.temp if entity @a[team=football_A]
execute if score #error_code football_lobby.temp matches 0 store result score #teamB_count football_lobby.temp if entity @a[team=football_B]

execute if score #error_code football_lobby.temp matches 0 \
    if score #teamA_count football_lobby.temp matches ..1 \
    run scoreboard players set #error_code football_lobby.temp 2
execute if score #error_code football_lobby.temp matches 0 \
    if score #teamB_count football_lobby.temp matches ..1 \
    run scoreboard players set #error_code football_lobby.temp 3

execute if score #error_code football_lobby.temp matches 0 run return run match start

# error msg
execute if score #error_code football_lobby.temp matches 1 run tellraw @s {text:"比赛仍在进行中，无法再次开始游戏！",color:"red",bold:true}
execute if score #error_code football_lobby.temp matches 2 run tellraw @s {text:"A队人数不足！",color:"red",bold:true}
execute if score #error_code football_lobby.temp matches 3 run tellraw @s {text:"B队人数不足！",color:"red",bold:true}

execute unless score #error_code football_lobby.temp matches 0 at @s run playsound block.anvil.place player @s ~ ~ ~ 0.7 1
