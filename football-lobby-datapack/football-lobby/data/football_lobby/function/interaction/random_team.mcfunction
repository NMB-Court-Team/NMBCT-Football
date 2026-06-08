advancement revoke @s only football_lobby:interaction/random_team

execute at @s run playsound ui.button.click player @s ~ ~ ~ 1 1

match clear

execute store result score #non-spec_count football_lobby.temp if entity @a[team=!spec]

# 红队人数 = 非观众人数 / 2
scoreboard players operation #team-red_count football_lobby.temp = #non-spec_count football_lobby.temp
scoreboard players operation #team-red_count football_lobby.temp /= #2 football_lobby.const

# 蓝队人数 = 非观众人数 - 红队人数
scoreboard players operation #team-blue_count football_lobby.temp = #non-spec_count football_lobby.temp
scoreboard players operation #team-blue_count football_lobby.temp -= #team-red_count football_lobby.temp

execute store result storage football_lobby:temp team_red_count int 1 run scoreboard players get #team-red_count football_lobby.temp
execute store result storage football_lobby:temp team_blue_count int 1 run scoreboard players get #team-blue_count football_lobby.temp

function football_lobby:interaction/random_team.select with storage football_lobby:temp

tellraw @a {"text":"已随机分配队伍！","color":"green"}
