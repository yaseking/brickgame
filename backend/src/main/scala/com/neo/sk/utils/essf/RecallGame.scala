package com.neo.sk.utils.essf
import com.neo.sk.carnie.paperClient.Protocol._
import org.seekloud.essf.io.{FrameData, FrameInputStream}
import org.seekloud.byteobject.ByteObject._
import org.seekloud.byteobject.{MiddleBuffer, MiddleBufferInJvm}

import scala.collection.mutable
/**
  * Created by haoshuhan on 2018/10/12.
  */
object RecallGame {

  def initInput(targetFile: String): FrameInputStream = {
    new FrameInputStream(targetFile)
  }

  /**解码*/

  def metaDataDecode(a:Array[Byte])={
    val buffer = new MiddleBufferInJvm(a)
    bytesDecode[GameInformation](buffer)
  }

  def initStateDecode(a:Array[Byte]) ={
    val buffer = new MiddleBufferInJvm(a)
    bytesDecode[Snapshot](buffer)
  }

  def userMapDecode(a:Array[Byte])={
    val buffer = new MiddleBufferInJvm(a)
    bytesDecode[EssfMapInfo](buffer)
  }

//  def userMapEncode(u:mutable.HashMap[EssfMapKey,EssfMapJoinLeftInfo])(implicit middleBuffer: MiddleBufferInJvm)={
//    EssfMapInfo(u.toList).fillMiddleBuffer(middleBuffer).result()
//  }

}
