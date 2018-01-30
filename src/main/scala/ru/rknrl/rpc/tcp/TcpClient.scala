//       ___       ___       ___       ___       ___
//      /\  \     /\__\     /\__\     /\  \     /\__\
//     /::\  \   /:/ _/_   /:| _|_   /::\  \   /:/  /
//    /::\:\__\ /::-"\__\ /::|/\__\ /::\:\__\ /:/__/
//    \;:::/  / \;:;-",-" \/|::/  / \;:::/  / \:\  \
//     |:\/__/   |:|  |     |:/  /   |:\/__/   \:\__\
//      \|__|     \|__|     \/__/     \|__|     \/__/

package ru.rknrl.rpc.tcp

import java.net.InetSocketAddress

import akka.actor.{ActorRef, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import ru.rknrl.rpc.Serializer
import ru.rknrl.rpc.tcp.TcpClient.ConnectionClosedException

object TcpClient {
  def props(host: String, port: Int, acceptWithActor: ActorRef ⇒ Props, serializer: Serializer) =
    Props(classOf[TcpClient], host, port, acceptWithActor, serializer)

  class ConnectionClosedException extends Exception

}

class TcpClient(host: String, port: Int, acceptWithActor: ActorRef ⇒ Props, serializer: Serializer) extends ClientSessionBase(acceptWithActor, serializer) {
  val address = new InetSocketAddress(host, port)

  import context.system

  var tcp: ActorRef = _

  IO(Tcp) ! Connect(address)

  val connectionReceive: Receive = {
    case _: ConnectionClosed ⇒
      log.debug("connection closed")
      throw new ConnectionClosedException()

    case CommandFailed(e) ⇒
      log.error("command failed " + e)
      throw new ConnectionClosedException()

    case Connected(remote, local) ⇒
      val name = remote.getAddress.getHostAddress + ":" + remote.getPort
      log.debug("connected " + name)

      tcp = sender
      tcp ! Register(self)
  }

  override def receive = connectionReceive orElse super.receive
}
