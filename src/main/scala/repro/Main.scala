package repro

import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.http.javadsl.model.headers.CustomHeader
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.stream.ActorMaterializer
import akka.util.ByteString

import scala.concurrent.duration._
import scala.concurrent.Await

object Main {
  def main(args: Array[String]): Unit = {
    val times = args(0).toInt

    implicit val system = ActorSystem()
    import system.dispatcher
    implicit val materializer = ActorMaterializer()
    val http = Http()

    // https doesn't close connection properly
    // [ERROR] [02/04/2017 00:00:22.882] [default-akka.actor.default-dispatcher-7]
    // [akka.actor.ActorSystemImpl(default)] Outgoing request stream error
    // (akka.stream.AbruptTerminationException)
    val req = HttpRequest(uri = Uri("https://www.facebook.com/"))
    val responses = (1 to times).map { num =>
      val response = http.singleRequest(req).flatMap { r =>
        r.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).map((r, _))
      }
      // Consume the response, thus closing the request
      val (resp, bytes) = Await.result(response, 10.seconds)
      println(s"$num -> $resp")
      new String(bytes.toArray, StandardCharsets.UTF_8)
    }

    println()
    println(responses.headOption)

    http.shutdownAllConnectionPools().onComplete { _ =>
      system.terminate().onComplete(println)
    }
  }
}