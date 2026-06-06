match join spec
tellraw @a [{text:"",color:yellow},{text:"[比赛大厅] ",color:"gold"},{selector:"@s",color:green}," 加入了 ",{text:"旁观",color:gray,bold:true}]
execute at @s run playsound block.note_block.pling player @s ~ ~ ~ 0.7 1.5
