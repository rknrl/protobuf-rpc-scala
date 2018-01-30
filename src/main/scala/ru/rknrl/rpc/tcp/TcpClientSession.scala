//       ___       ___       ___       ___       ___
//      /\  \     /\__\     /\__\     /\  \     /\__\
//     /::\  \   /:/ _/_   /:| _|_   /::\  \   /:/  /
//    /::\:\__\ /::-"\__\ /::|/\__\ /::\:\__\ /:/__/
//    \;:::/  / \;:;-",-" \/|::/  / \;:::/  / \:\  \
//     |:\/__/   |:|  |     |:/  /   |:\/__/   \:\__\
//      \|__|     \|__|     \/__/     \|__|     \/__/

package ru.rknrl.rpc.tcp

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.Tcp
import akka.io.Tcp._
import akka.util.ByteString
import com.trueaccord.scalapb.GeneratedMessage
import ru.rknrl.rpc.tcp.TcpClientSession.CloseConnection
import ru.rknrl.rpc.{MessageSerialization, Serializer}

object TcpClientSession {

  case object CloseConnection

  def props(tcp: ActorRef, acceptWithActor: ActorRef ⇒ Props, serializer: Serializer) =
    Props(classOf[TcpClientSession], tcp, acceptWithActor, serializer)
}

class TcpClientSession(var tcp: ActorRef, acceptWithActor: ActorRef ⇒ Props, serializer: Serializer) extends ClientSessionBase(acceptWithActor, serializer) {
  tcp ! Register(self)
}

abstract class ClientSessionBase(acceptWithActor: ActorRef ⇒ Props, serializer: Serializer) extends Actor with ActorLogging {

  var tcp: ActorRef

  case object Ack extends Event

  var receiveBuffer = ByteString.empty

  val sendBuffer = ByteString.newBuilder

  var waitForAck = false

  val client = context.actorOf(acceptWithActor(self), "client-" + self.path.name.substring("client-session-".length))

  def receive: Receive = {
    case Received(receivedData) ⇒
      onReceive(receivedData)

    case _: ConnectionClosed ⇒
      log.debug("connection closed")
      context stop self

    case msg: GeneratedMessage ⇒ send(msg)

    case CommandFailed(e) ⇒
      log.error("command failed " + e)
      context stop self

    case Ack ⇒
      waitForAck = false
      flush()

    case CloseConnection ⇒ tcp ! Tcp.Close
  }

  def onReceive(receivedData: ByteString): Unit = {
    val data = receiveBuffer ++ receivedData
    val (newBuffer, messages) = MessageSerialization.extractMessages(data, serializer)
    for (msg ← messages) client ! msg
    receiveBuffer = newBuffer
  }

  def send(msg: GeneratedMessage): Unit = {
    sendBuffer.append(MessageSerialization.write(msg, serializer))
    flush()
  }

  def flush(): Unit =
    if (!waitForAck && sendBuffer.length > 0) {
      waitForAck = true
      tcp ! Write(sendBuffer.result.compact, Ack)
      sendBuffer.clear()
    }
}


