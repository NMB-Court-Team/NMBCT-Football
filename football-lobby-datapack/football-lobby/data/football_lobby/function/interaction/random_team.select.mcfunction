# @param team_red_count: int  红队人数
# @param team_blue_count: int  蓝队人数

$execute as @a[team=,sort=random,limit=$(team_red_count)] run match join A
$execute as @a[team=,sort=random,limit=$(team_blue_count)] run match join B

# team= means "players that are not in any team"
# team=! means "players that are in any team"
