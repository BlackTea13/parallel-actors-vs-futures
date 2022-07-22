import akka.actor.*
import akka.pattern.ask
import akka.util.Timeout
import concurrent.duration.DurationInt
import scala.concurrent.Await

object main extends App {
  case class Benchmark(FuturesTime: Double, ActorsTime: Double)
  def test (basePath: String): Benchmark = {
    // Future Crawler
    val (wsF, timeF) = MiscMethods.timed(FutureCrawler.crawlForStats, basePath)
    println(s"Future Crawler took: $timeF s with result: $wsF")

    // Actor Crawler
    import ActorCrawler._
    val start = System.nanoTime()
    implicit val timeout: Timeout = Timeout(120.seconds)
    val actorSystem = ActorSystem("ActorCrawlerSystem")
    val requester = actorSystem.actorOf(Props(classOf[ActorCrawler.test]), "RequesterCrawler")
    val ws = requester ? StartCrawl(basePath)
    val result = Await.result(ws, timeout.duration).asInstanceOf[WebStats]
    actorSystem.terminate()
    val end = System.nanoTime()
    val timeA = (end - start) / 1e9d
    println(s"Actor Crawler took: $timeA with result: $result")
    Benchmark(timeF, timeA)
  }

  case class Stats(FMean: Double, AMean: Double)
  def runBenchmark(n: Int, basePath: String) = {
    var results : List[Benchmark] = List()
    for( i <- 0 to n ){
      val run = test(basePath)
      results = run :: results
    }
    val FMean = results.map(x => x.FuturesTime).sum / results.size
    val AMean = results.map(x => x.ActorsTime).sum / results.size
    println(s"TEST RESULTS ==================================================")
    println(s"testing link START: $basePath \nFuture Mean: $FMean, Actor Mean: $AMean")

    val FStdDev = stdDev(results.map(x => x.FuturesTime), FMean)
    val AStdDev = stdDev(results.map(x => x.ActorsTime), AMean)
    println(s"Future stdDev: $FStdDev, Actor stdDev: $AStdDev")
  }

  def stdDev(xs: List[Double], mean: Double): Double = {
    math.sqrt(xs.map(x => math.pow(x - mean, 2)).sum / xs.size)
  }

  val TEST_LINK1 = "https://cs.muic.mahidol.ac.th/courses/ooc/api/"
  val TEST_LINK2 = ""
  runBenchmark(10, TEST_LINK1)

}
