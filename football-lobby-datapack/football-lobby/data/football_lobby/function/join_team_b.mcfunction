match join B
tellraw @a [{text:"",color:yellow},{text:"[比赛大厅] ",color:"gold"},{selector:"@s",color:green}," 加入了 ",{text:"蓝队",color:blue,bold:true}]
execute at @s run playsound block.note_block.pling player @s ~ ~ ~ 0.7 1.5
