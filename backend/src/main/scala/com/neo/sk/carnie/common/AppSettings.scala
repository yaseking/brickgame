package com.neo.sk.carnie.common

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

/**
  * User: Taoz
  * Date: 9/4/2015
  * Time: 4:29 PM
  */
object AppSettings {


  val log = LoggerFactory.getLogger(this.getClass)
  val config = ConfigFactory.parseResources("product.conf").withFallback(ConfigFactory.load())

  val appConfig = config.getConfig("app")

  val httpInterface = appConfig.getString("http.interface")
  val httpPort = appConfig.getInt("http.port")

  val esheepConfig = config.getConfig("dependence.esheep")
  val esheepProtocol = esheepConfig.getString("protocol")
  val esheepDomain = esheepConfig.getString("domain")
  val esheepUrl = esheepConfig.getString("url")
  val esheepAppId = esheepConfig.getString("appId")
  val esheepSecureKey = esheepConfig.getString("secureKey")
  val esheepGameId = esheepConfig.getLong("gameId")
  val esheepGsKey = esheepConfig.getString("gsKey")

}
