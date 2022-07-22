import akka.actor.*
import akka.pattern.ask
import akka.util.Timeout
import concurrent.duration.DurationInt
import scala.concurrent.Await

object main extends App {
  val START_LINK = "https://cs.muic.mahidol.ac.th/courses/ooc/api/"

  // Future Crawler
  val (wsF, timeF) = MiscMethods.timed(FutureCrawler.crawlForStats, START_LINK)
  println(s"Future Crawler took: $timeF s with result: $wsF")

  // Actor Crawler
  import ActorCrawler._
  val start = System.currentTimeMillis()
  implicit val timeout: Timeout = Timeout(120.seconds)
  val actorSystem = ActorSystem("ActorCrawlerSystem")
  val requester = actorSystem.actorOf(Props(classOf[ActorCrawler.test]), "RequesterCrawler")
  val ws = requester ? StartCrawl(START_LINK)
  val result = Await.result(ws, timeout.duration).asInstanceOf[WebStats]
  actorSystem.terminate()
  val end = System.currentTimeMillis()
  val timeA = (end - start) / 1000.0
  println(s"Actor Crawler took: $timeA with result: $result")

}
