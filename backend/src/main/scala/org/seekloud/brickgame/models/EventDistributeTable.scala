package org.seekloud.brickgame.models

import scala.concurrent.Future

/**
  * Created by dry on 2018/12/4.
  **/

//case class EventDistribute(distributor: String, ip: String, port: String, domain: String, receiveAppId: String, checkAppId: String, checkSecureKey: String)
case class PlayerInfo(id: Int, username: String, password: String)

trait PlayerInfoTable {
  import org.seekloud.utils.DBUtil.driver.api._

  class PlayerInfoTable(tag: Tag) extends Table[PlayerInfo](tag, "EVENT_DISTRIBUTE") {
    val id = column[Int]("ID", O.PrimaryKey)
    val username = column[String]("IP")
    val password = column[String]("PORT")
//    val domain = column[String]("DOMAIN")
//    val receiveAppId = column[String]("RECEIVE_APPID")
//    val checkAppId = column[String]("CHECK_APPID")
//    val checkSecureKey = column[String]("CHECK_SECUREKEY")


//    def * = (distributor, ip, port, domain, receiveAppId, checkAppId, checkSecureKey) <> (EventDistribute.tupled, EventDistribute.unapply)
    def * = (id, username, password) <> (PlayerInfo.tupled, PlayerInfo.unapply)
  }

  protected val playerInfoTableQuery = TableQuery[PlayerInfoTable]

}

//object EventDistributeRepo extends EventDistributeTable {
//
//  import org.seekloud.utils.DBUtil.driver.api._
//  import org.seekloud.utils.DBUtil.db
//
//  def create(): Future[Unit] = {
//    db.run(eventDistributeTableQuery.schema.create)
//  }
//
//
//  def getAllEventInfo: Future[List[EventDistribute]] = {
//    db.run (eventDistributeTableQuery.to[List].result)
//  }
//
//  def updateEventInfo(eventInfo: EventDistribute): Future[Int] = {
//    db.run(eventDistributeTableQuery.insertOrUpdate(eventInfo))
//  }
//
//  def deleteEventInfo(distributor: String): Future[Int] = {
//    db.run(eventDistributeTableQuery.filter(i => i.distributor === distributor).delete)
//  }
//
//  def getEventInfo(distributor: String): Future[Option[EventDistribute]] = {
//    db.run (eventDistributeTableQuery.filter(i => i.distributor === distributor).result.headOption)
//  }
//
//}
