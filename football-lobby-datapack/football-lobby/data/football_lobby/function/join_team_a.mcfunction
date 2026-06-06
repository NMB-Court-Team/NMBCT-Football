match join A
tellraw @a [{text:"",color:yellow},{text:"[比赛大厅] ",color:"gold"},{selector:"@s",color:green}," 加入了 ",{text:"红队",color:red,bold:true}]
execute at @s run playsound block.note_block.pling player @s ~ ~ ~ 0.7 1.5
