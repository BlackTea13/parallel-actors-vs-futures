object main extends App {
  val START_LINK = "https://cs.muic.mahidol.ac.th/courses/ooc/api"
  //val futureWS = FutureCrawler.crawlForStats(START_LINK)
  //println(futureWS)
  ActorCrawler.crawlForStats(START_LINK)
}
// WebStats(10173,5,HashMap(jpg -> 2, png -> 123, css -> 3, gif -> 45, js -> 5, html -> 10173),28279929)