//       ___       ___       ___       ___       ___
//      /\  \     /\__\     /\__\     /\  \     /\__\
//     /::\  \   /:/ _/_   /:| _|_   /::\  \   /:/  /
//    /::\:\__\ /::-"\__\ /::|/\__\ /::\:\__\ /:/__/
//    \;:::/  / \;:;-",-" \/|::/  / \;:::/  / \:\  \
//     |:\/__/   |:|  |     |:/  /   |:\/__/   \:\__\
//      \|__|     \|__|     \/__/     \|__|     \/__/

package ru.rknrl.rpc

import akka.util.ByteString
import com.trueaccord.scalapb.GeneratedMessage

import scala.annotation.tailrec

object MessageSerialization {
  implicit val byteOrder = java.nio.ByteOrder.LITTLE_ENDIAN

  val headerSize = 4 + 4

  val maxSize = 512 * 1024

  def extractMessages(data: ByteString, serializer: Serializer): (ByteString, Seq[Any]) = {
    val (newBuffer, frames) = MessageSerialization.extractFrames(data, Nil)

    (newBuffer, frames.map(f ⇒ serializer.bytesToMessage(f.msgId, f.byteString)))
  }

  private case class Frame(msgId: Int, byteString: ByteString)

  @tailrec
  private def extractFrames(data: ByteString, frames: List[Frame]): (ByteString, Seq[Frame]) =
    if (data.length < headerSize)
      (data.compact, frames)
    else {
      val iterator = data.iterator
      val msgId = iterator.getInt
      val size = iterator.getInt

      if (size < 0 || size > maxSize)
        throw new IllegalArgumentException(s"received too large frame of size $size (max = $maxSize)")

      val totalSize = headerSize + size
      if (data.length >= totalSize)
        extractFrames(data drop totalSize, frames :+ Frame(msgId, data.slice(headerSize, totalSize)))
      else
        (data.compact, frames)
    }

  def write(msg: GeneratedMessage, serializer: Serializer): ByteString = {
    val msgBuilder = ByteString.newBuilder
    val os = msgBuilder.asOutputStream
    msg.writeDelimitedTo(os)
    val msgByteString = msgBuilder.result()

    val msgId = serializer.messageToId(msg)
    val builder = ByteString.newBuilder
    builder.putInt(msgId)
    builder.putInt(msgByteString.length)
    builder.append(msgByteString)
    builder.result()
  }

}
