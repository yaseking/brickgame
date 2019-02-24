package org.seekloud.brickgame.models

import scala.concurrent.Future

/**
  * Created by lty on 2019/2/22.
  **/

case class ActiveUser(id: Int, username: String, leaveTime: Long)

trait ActiveUserTable {
  import org.seekloud.utils.DBUtil.driver.api._

  class ActiveUserTable(tag: Tag) extends Table[ActiveUser](tag, "ACTIVE_USER") {
    val id = column[Int]("ID", O.AutoInc, O.PrimaryKey)
    val username = column[String]("USERNAME")
    val leaveTime = column[Long]("LEAVE_TIME")
    def * = (id, username, leaveTime) <> (ActiveUser.tupled, ActiveUser.unapply)
  }

  protected val activeUserTableQuery = TableQuery[ActiveUserTable]

}

object ActiveUserRepo extends ActiveUserTable {

  import org.seekloud.utils.DBUtil.driver.api._
  import org.seekloud.utils.DBUtil.db

  def create(): Future[Unit] = {
    db.run(activeUserTableQuery.schema.create)
  }

  def updatePlayerInfo(eventInfo: ActiveUser): Future[Int] = {
    db.run(activeUserTableQuery.insertOrUpdate(eventInfo))
  }

  def getRecords = {
    db.run (activeUserTableQuery.sortBy(_.leaveTime.desc).result)
  }


}
