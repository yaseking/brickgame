package org.seekloud.brickgame.http

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.stream.{Materializer, OverflowStrategy}
import akka.util.Timeout

import scala.concurrent.ExecutionContextExecutor

/**
  * User: Taoz
  * Date: 8/26/2016
  * Time: 10:27 PM
  */
trait HttpService extends PlayerService
  with ResourceService
  with EsheepService
  with AdminService {


  implicit val system: ActorSystem

  implicit val executor: ExecutionContextExecutor

  implicit val materializer: Materializer

  implicit val timeout: Timeout

//  private val home = get {
//    redirect(Uri(s"$httpUrl"),StatusCodes.SeeOther)
//  }

  val routes = ignoreTrailingSlash{
    pathPrefix("brickgame") {
      netSnakeRoute ~
        resourceRoutes ~
        esheepRoute ~
        adminRoutes
    }// ~ home
  }
}
