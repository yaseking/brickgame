package com.neo.sk.carnie.common

import com.typesafe.config.ConfigFactory

/**
  * Created by haoshuhan on 2018/12/12.
  */
object BotAppSetting {
  val botConfig = ConfigFactory.parseResources("bot.conf").withFallback(ConfigFactory.load())

  val appConfig = botConfig.getConfig("app")

  val userConfig = appConfig.getConfig("user")
  val email = userConfig.getString("email")
  val psw = userConfig.getString("psw")

  val render = appConfig.getBoolean("render")

  val apiToken = appConfig.getString("apiToken")

}
