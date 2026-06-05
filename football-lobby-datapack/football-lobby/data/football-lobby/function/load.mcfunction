execute unless data storage football-lobby:points initialized run function football-lobby:init
execute unless data storage football-lobby:points start_button_rotation run data modify storage football-lobby:points start_button_rotation set value [0.0f,0.0f]
execute unless data storage football-lobby:points settings_button_rotation run data modify storage football-lobby:points settings_button_rotation set value [0.0f,0.0f]
execute unless data storage football-lobby:points team_spec run data modify storage football-lobby:points team_spec set value [0.5d,64.0d,4.5d]
execute unless data storage football-lobby:config particles_per_team_per_tick run data modify storage football-lobby:config particles_per_team_per_tick set value 3
scoreboard objectives add football_lobby dummy
schedule function football-lobby:reload 1t replace
