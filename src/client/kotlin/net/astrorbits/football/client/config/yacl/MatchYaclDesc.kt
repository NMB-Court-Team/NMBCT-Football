package net.astrorbits.football.client.config.yacl

/** YACL 选项说明：与名称键同名并追加 `.desc`。 */
object MatchYaclDesc {
    fun desc(nameKey: String): String = "$nameKey.desc"
}
