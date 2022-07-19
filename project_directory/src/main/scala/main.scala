import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import scala.language.implicitConversions

object main extends App {
  val START_LINK = "https://cs.muic.mahidol.ac.th/courses/ooc/api"
  val futureWS = FutureCrawler.crawlForStats(START_LINK)
  println(futureWS)
}
