package org.seekloud.brickgame.models

import scala.concurrent.Future

/**
  * Created by lty on 2019/2/22.
  **/

case class PlayerInfo(id: Int, username: String, password: String, state: Boolean)

trait PlayerInfoTable {
  import org.seekloud.utils.DBUtil.driver.api._

  class PlayerInfoTable(tag: Tag) extends Table[PlayerInfo](tag, "PLAYER_INFO") {
    val id = column[Int]("ID", O.AutoInc, O.PrimaryKey)
    val username = column[String]("USERNAME")
    val password = column[String]("PASSWORD")
    val state = column[Boolean]("STATE")
    def * = (id, username, password, state) <> (PlayerInfo.tupled, PlayerInfo.unapply)
  }

  protected val playerInfoTableQuery = TableQuery[PlayerInfoTable]

}

object PlayerInfoRepo extends PlayerInfoTable {

  import org.seekloud.utils.DBUtil.driver.api._
  import org.seekloud.utils.DBUtil.db

  def create(): Future[Unit] = {
    db.run(playerInfoTableQuery.schema.create)
  }


  def getAllPlayers: Future[List[PlayerInfo]] = {
    db.run (playerInfoTableQuery.to[List].result)
  }

  def updatePlayerInfo(eventInfo: PlayerInfo): Future[Int] = {
    db.run(playerInfoTableQuery.insertOrUpdate(eventInfo))
  }

  def getPlayerByName(name: String) = {
    db.run(playerInfoTableQuery.filter(_.username===name).result.headOption)
  }

  def forbidPlayer(name: String) = {
    db.run(playerInfoTableQuery.filter(_.username===name).map(i => i.state).update(false))
  }

  def enablePlayer(name: String) = {
    db.run(playerInfoTableQuery.filter(_.username===name).map(i => i.state).update(true))
  }

//  def deleteEventInfo(distributor: String): Future[Int] = {
//    db.run(playerInfoTableQuery.filter(i => i.distributor === distributor).delete)
//  }

//  def getEventInfo(distributor: String): Future[Option[EventDistribute]] = {
//    db.run (playerInfoTableQuery.filter(i => i.distributor === distributor).result.headOption)
//  }

}
