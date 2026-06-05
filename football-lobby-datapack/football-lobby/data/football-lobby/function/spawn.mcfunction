$summon marker $(team_a_x) $(team_a_y) $(team_a_z) {Tags:["football_lobby_entity","football_lobby_team_a"]}
$summon marker $(team_b_x) $(team_b_y) $(team_b_z) {Tags:["football_lobby_entity","football_lobby_team_b"]}
$summon marker $(team_spec_x) $(team_spec_y) $(team_spec_z) {Tags:["football_lobby_entity","football_lobby_team_spec"]}
$summon marker $(start_x) $(start_y) $(start_z) {Tags:["football_lobby_entity","football_lobby_start_anchor"]}
data modify entity @e[type=marker,tag=football_lobby_start_anchor,limit=1] Rotation set from storage football-lobby:points start_button_rotation
execute at @e[type=marker,tag=football_lobby_start_anchor,limit=1] run summon interaction ^ ^1 ^-.75 {Tags:["football_lobby_entity","football_lobby_start"],width:1.8f,height:1.0f,response:1b}
execute at @e[type=marker,tag=football_lobby_start_anchor,limit=1] positioned ~ ~1.15 ~ run summon text_display ~ ~ ~ {Tags:["football_lobby_entity","football_lobby_start_text"],text:{text:"+----------+\n| 开始比赛 |\n+----------+",color:"green",bold:1b},billboard:"fixed",alignment:"center",line_width:200,background:0,see_through:0b,shadow:1b}
data modify entity @e[type=text_display,tag=football_lobby_start_text,limit=1] Rotation set from entity @e[type=marker,tag=football_lobby_start_anchor,limit=1] Rotation
$summon marker $(settings_x) $(settings_y) $(settings_z) {Tags:["football_lobby_entity","football_lobby_settings_anchor"]}
data modify entity @e[type=marker,tag=football_lobby_settings_anchor,limit=1] Rotation set from storage football-lobby:points settings_button_rotation
execute at @e[type=marker,tag=football_lobby_settings_anchor,limit=1] run summon interaction ^ ^1 ^-.75 {Tags:["football_lobby_entity","football_lobby_settings"],width:1.8f,height:1.0f,response:1b}
execute at @e[type=marker,tag=football_lobby_settings_anchor,limit=1] positioned ~ ~1.15 ~ run summon text_display ~ ~ ~ {Tags:["football_lobby_entity","football_lobby_settings_text"],text:{text:"+----------+\n| 比赛设置 |\n+----------+",color:"yellow",bold:1b},billboard:"fixed",alignment:"center",line_width:200,background:0,see_through:0b,shadow:1b}
data modify entity @e[type=text_display,tag=football_lobby_settings_text,limit=1] Rotation set from entity @e[type=marker,tag=football_lobby_settings_anchor,limit=1] Rotation
