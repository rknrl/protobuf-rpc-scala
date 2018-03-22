//       ___       ___       ___       ___       ___
//      /\  \     /\__\     /\__\     /\  \     /\__\
//     /::\  \   /:/ _/_   /:| _|_   /::\  \   /:/  /
//    /::\:\__\ /::-"\__\ /::|/\__\ /::\:\__\ /:/__/
//    \;:::/  / \;:;-",-" \/|::/  / \;:::/  / \:\  \
//     |:\/__/   |:|  |     |:/  /   |:\/__/   \:\__\
//      \|__|     \|__|     \/__/     \|__|     \/__/

package ru.rknrl.rpc.webSocket

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.server.Directives.{handleWebSocketMessages, path}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import ru.rknrl.rpc.Serializer
import ru.rknrl.rpc.webSocket.WebSocketClientSession.WebSocketConnected

object WebSocketServer {

  def props(host: String, port: Int, acceptWithActor: ActorRef ⇒ Props, serializer: Serializer) =
    Props(classOf[WebSocketServer], host, port, acceptWithActor, serializer)
}

class WebSocketServer(host: String, port: Int, acceptWithActor: ActorRef ⇒ Props, serializer: Serializer) extends Actor with ActorLogging {
  val address = new InetSocketAddress(host, port)
  val messagePoolSize = 10

  import context.dispatcher

  implicit val system = context.system

  implicit val materializer = ActorMaterializer()

  def flow(): Flow[Message, Message, Any] = {
    val clientRef = context.actorOf(WebSocketClientSession.props(acceptWithActor, serializer))

    val in = Sink.actorRef(clientRef, WebSocketConnected)

    val out = Source.actorRef(messagePoolSize, OverflowStrategy.fail).mapMaterializedValue { a ⇒
      clientRef ! WebSocketConnected(a)
      a
    }

    Flow.fromSinkAndSource(in, out)
  }

  val route = path("ws") {
    handleWebSocketMessages(flow())
  }

  val bindingFuture = Http().bindAndHandle(route, host, port)

  def receive = {
    case _ ⇒
  }

  override def postStop(): Unit = {
    bindingFuture.foreach(_.unbind())
    super.postStop()
  }
}

