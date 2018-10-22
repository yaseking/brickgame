package com.neo.sk.carnie.models

// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object SlickTables extends {
  val profile = slick.jdbc.PostgresProfile
} with SlickTables

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait SlickTables {
  val profile: slick.jdbc.JdbcProfile
  import profile.api._
  import slick.model.ForeignKeyAction
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import slick.jdbc.{GetResult => GR}

  /** DDL for all tables. Call .create to execute. */
  lazy val schema: profile.SchemaDescription = tGameVideo.schema ++ tUserInVideo.schema
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table tGameVideo
   *  @param videoId Database column video_id SqlType(bigserial), AutoInc, PrimaryKey
   *  @param roomId Database column room_id SqlType(int4)
   *  @param startTime Database column start_time SqlType(int8)
   *  @param endTime Database column end_time SqlType(int8) */
  case class rGameVideo(videoId: Long, roomId: Int, startTime: Long, endTime: Long)
  /** GetResult implicit for fetching rGameVideo objects using plain SQL queries */
  implicit def GetResultrGameVideo(implicit e0: GR[Long], e1: GR[Int]): GR[rGameVideo] = GR{
    prs => import prs._
    rGameVideo.tupled((<<[Long], <<[Int], <<[Long], <<[Long]))
  }
  /** Table description of table game_video. Objects of this class serve as prototypes for rows in queries. */
  class tGameVideo(_tableTag: Tag) extends profile.api.Table[rGameVideo](_tableTag, "game_video") {
    def * = (videoId, roomId, startTime, endTime) <> (rGameVideo.tupled, rGameVideo.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(videoId), Rep.Some(roomId), Rep.Some(startTime), Rep.Some(endTime)).shaped.<>({r=>import r._; _1.map(_=> rGameVideo.tupled((_1.get, _2.get, _3.get, _4.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column video_id SqlType(bigserial), AutoInc, PrimaryKey */
    val videoId: Rep[Long] = column[Long]("video_id", O.AutoInc, O.PrimaryKey)
    /** Database column room_id SqlType(int4) */
    val roomId: Rep[Int] = column[Int]("room_id")
    /** Database column start_time SqlType(int8) */
    val startTime: Rep[Long] = column[Long]("start_time")
    /** Database column end_time SqlType(int8) */
    val endTime: Rep[Long] = column[Long]("end_time")
  }
  /** Collection-like TableQuery object for table tGameVideo */
  lazy val tGameVideo = new TableQuery(tag => new tGameVideo(tag))

  /** Entity class storing rows of table tUserInVideo
   *  @param userId Database column user_id SqlType(varchar), Length(255,true)
   *  @param videoId Database column video_id SqlType(int8)
   *  @param roomId Database column room_id SqlType(int4) */
  case class rUserInVideo(userId: String, videoId: Long, roomId: Int)
  /** GetResult implicit for fetching rUserInVideo objects using plain SQL queries */
  implicit def GetResultrUserInVideo(implicit e0: GR[String], e1: GR[Long], e2: GR[Int]): GR[rUserInVideo] = GR{
    prs => import prs._
    rUserInVideo.tupled((<<[String], <<[Long], <<[Int]))
  }
  /** Table description of table user_in_video. Objects of this class serve as prototypes for rows in queries. */
  class tUserInVideo(_tableTag: Tag) extends profile.api.Table[rUserInVideo](_tableTag, "user_in_video") {
    def * = (userId, videoId, roomId) <> (rUserInVideo.tupled, rUserInVideo.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(userId), Rep.Some(videoId), Rep.Some(roomId)).shaped.<>({r=>import r._; _1.map(_=> rUserInVideo.tupled((_1.get, _2.get, _3.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column user_id SqlType(varchar), Length(255,true) */
    val userId: Rep[String] = column[String]("user_id", O.Length(255,varying=true))
    /** Database column video_id SqlType(int8) */
    val videoId: Rep[Long] = column[Long]("video_id")
    /** Database column room_id SqlType(int4) */
    val roomId: Rep[Int] = column[Int]("room_id")

    /** Index over (videoId) (database name user_in_video_video_id_idx) */
    val index1 = index("user_in_video_video_id_idx", videoId)
  }
  /** Collection-like TableQuery object for table tUserInVideo */
  lazy val tUserInVideo = new TableQuery(tag => new tUserInVideo(tag))
}
