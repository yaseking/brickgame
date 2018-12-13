//package com.neo.sk.carnie.controller
//
//import com.neo.sk.carnie.Boot
//import com.neo.sk.carnie.common.Context
//import com.neo.sk.carnie.scene._
//
///**
//  * User: Jason
//  * Date: 2018/12/13
//  * Time: 17:06
//  */
//class BotLoginController(botLoginScene: BotLoginScene, context: Context) {
//  botLoginScene.setListener(new BotLoginSceneListener{
//    override def login(botId: String, botKey: String): Unit = {
//
//    }
//  })
//
//  def showScene: Unit = {
//    Boot.addToPlatform {
//      context.switchScene(botLoginScene.getScene, "botLogin", false)
//    }
//  }
//}
