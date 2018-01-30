//       ___       ___       ___       ___       ___
//      /\  \     /\__\     /\__\     /\  \     /\__\
//     /::\  \   /:/ _/_   /:| _|_   /::\  \   /:/  /
//    /::\:\__\ /::-"\__\ /::|/\__\ /::\:\__\ /:/__/
//    \;:::/  / \;:;-",-" \/|::/  / \;:::/  / \:\  \
//     |:\/__/   |:|  |     |:/  /   |:\/__/   \:\__\
//      \|__|     \|__|     \/__/     \|__|     \/__/

package ru.rknrl.rpc.webSocket

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.http.scaladsl.model.ws.BinaryMessage
import akka.stream.ActorMaterializer
import akka.util.ByteString
import com.trueaccord.scalapb.GeneratedMessage
import ru.rknrl.rpc.tcp.TcpClientSession.CloseConnection
import ru.rknrl.rpc.webSocket.WebSocketClientSession.{WebClientDisconnected, WebSocketConnected}
import ru.rknrl.rpc.{MessageSerialization, Serializer}

object WebSocketClientSession {
  def props(acceptWithActor: ActorRef ⇒ Props, serializer: Serializer) =
    Props(classOf[WebSocketClientSession], acceptWithActor, serializer)

  case class WebSocketConnected(ref: ActorRef)

  case object WebClientDisconnected

}

private class WebSocketClientSession(acceptWithActor: ActorRef ⇒ Props,
                                     serializer: Serializer) extends Actor with ActorLogging {
  var webSocket = Option.empty[ActorRef]
  val client = context.actorOf(acceptWithActor(self))
  var receiveBuffer = ByteString.empty
  implicit val materializer = ActorMaterializer()

  def receive = {
    case msg: GeneratedMessage ⇒
      webSocket.foreach(_ ! BinaryMessage.Strict(MessageSerialization.write(msg, serializer)))

    case BinaryMessage.Strict(receivedData) ⇒
      onReceive(receivedData)

    case BinaryMessage.Streamed(receivedData) ⇒
      receivedData.runForeach(onReceive)

    case WebSocketConnected(ref: ActorRef) ⇒
      webSocket = Some(ref)
      context.watch(ref)

    case WebClientDisconnected ⇒
      webSocket = None
      context stop self

    case Terminated(a) if webSocket.contains(a) ⇒
      webSocket = None
      context stop self

    case CloseConnection ⇒
      webSocket = None
      context stop self
  }

  def onReceive(receivedData: ByteString): Unit = {
    val data = receiveBuffer ++ receivedData
    val (newBuffer, messages) = MessageSerialization.extractMessages(data, serializer)
    for (msg ← messages) client ! msg
    receiveBuffer = newBuffer
  }

  override def postStop(): Unit =
    webSocket.foreach(context.stop)
}
