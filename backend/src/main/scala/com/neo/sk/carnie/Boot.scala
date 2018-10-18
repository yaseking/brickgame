package com.neo.sk.carnie

import akka.actor.ActorSystem
import akka.actor.typed.ActorRef
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.neo.sk.carnie.http.HttpService
import scala.language.postfixOps
import com.neo.sk.carnie.core._
import akka.actor.typed.scaladsl.adapter._


/**
  * User: Taoz
  * Date: 8/26/2016
  * Time: 10:25 PM
  */
object Boot extends HttpService {

  import concurrent.duration._
  import com.neo.sk.carnie.common.AppSettings._


  override implicit val system = ActorSystem("hiStream", config)
  // the executor should not be the default dispatcher.
  override implicit val executor = system.dispatchers.lookup("akka.actor.my-blocking-dispatcher")
  override implicit val materializer = ActorMaterializer()

  override val timeout = Timeout(20 seconds) // for actor asks

  implicit val tokenActor: ActorRef[TokenActor.Command] = system.spawn(TokenActor.behavior, "tokenActor")

  val log: LoggingAdapter = Logging(system, getClass)



  def main(args: Array[String]) {
    log.info("Starting.")
    Http().bindAndHandle(routes, httpInterface, httpPort)
    log.info(s"Listen to the $httpInterface:$httpPort")
    log.info("Done.")
  }






}
