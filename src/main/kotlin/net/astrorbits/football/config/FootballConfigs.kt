package net.astrorbits.football.config

import net.astrorbits.football.config.client.FootballClientConfig
import net.astrorbits.football.config.client.FootballClientConfigHolder
import net.astrorbits.football.config.server.FootballServerConfig
import net.astrorbits.football.config.server.FootballServerConfigHolder

/** 全局配置访问入口。 */
object FootballConfigs {
    val server: FootballServerConfig
        get() = FootballServerConfigHolder.current

    val client: FootballClientConfig
        get() = FootballClientConfigHolder.current
}
