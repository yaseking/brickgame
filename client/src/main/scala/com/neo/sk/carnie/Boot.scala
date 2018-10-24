package com.neo.sk.carnie

import akka.actor.{ActorSystem, Scheduler}
import akka.stream.ActorMaterializer
import akka.actor.typed.scaladsl.adapter._
import scala.language.postfixOps
import akka.dispatch.MessageDispatcher
import com.neo.sk.carnie.paperClient.WebSocketClient

/**
  * Created by dry on 2018/10/23.
  **/
object Boot {

  import com.neo.sk.carnie.common.AppSetting._

  implicit val system: ActorSystem = ActorSystem("carnie", config)
  implicit val executor: MessageDispatcher = system.dispatchers.lookup("akka.actor.my-blocking-dispatcher")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val scheduler: Scheduler = system.scheduler

  def main(args: Array[String]): Unit = {
    val wsClient = system.spawn(WebSocketClient.create(), "wsClient")
  }
}
