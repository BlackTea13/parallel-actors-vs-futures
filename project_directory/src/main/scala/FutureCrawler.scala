import org.jsoup.nodes.Document
import org.jsoup.{HttpStatusException, Jsoup}
import java.net.{MalformedURLException, URI, URL}
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.collection.concurrent.TrieMap
import concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable
import collection.JavaConverters.*

object FutureCrawler {

  private val numFiles: AtomicInteger = AtomicInteger(0)
  private val numWords: AtomicInteger = AtomicInteger(0)
  private val extMap: mutable.Map[String, Int] = TrieMap[String, Int]()
  private val urlMapNonHtml: mutable.Map[String, Int] = TrieMap[String, Int]()
  private var basePath: String = ""


  def crawlForStats(basePath: String): WebStats = {
    this.basePath = basePath

    val mapOfLinks = bfs(extractLinksFromCurrentPage, this.basePath)
    createWebStats(mapOfLinks)
  }

  def bfs[V](nbrs: V => Set[V], src: V): Set[V] = {

    def expand(frontier: Set[V], visited: Set[V]): (Set[V],Set[V]) = {
      val future = Future.sequence(frontier.map(p => Future { nbrs(p) })).map(_.flatten)
      val result_ = Await.result(future, Duration.Inf).diff(visited)
      val visited_ = visited.concat(result_)
      (result_, visited_)
    }

    @tailrec
    def iterate(frontier: Set[V], visited: Set[V]): Set[V] = {
      if frontier.nonEmpty then
        val (frontier_, visited_) = expand(frontier, visited)
        iterate(frontier_, visited_)
      else visited
    }

    iterate(Set(src), Set(src))
  }
  
  private def extractLinksFromCurrentPage(url: String): Set[String] = {
    try {
      val doc = Jsoup.connect(url).get
      val allLinks = doc.select("a[href]").asScala.map(link => {
        try {
          val uri = URL(link.attr("abs:href")).toURI
          if uri.getFragment != null
            then
              URI(uri.getScheme, uri.getSchemeSpecificPart, null).toURL.toString
            else
              link.attr("abs:href")
        }
        catch {
          case _ : Exception => ""
        }
      }).filter(_.startsWith(basePath))

      numWords.addAndGet(doc.text().split("\\s+").count(MiscMethods.isWord))
      addExtensionToMap(doc,"img[src]", "abs:src")
      addExtensionToMap(doc, "link[href]", "abs:href")
      addExtensionToMap(doc, "script[src]", "abs:src")
      addExtensionToMap(doc, "iframe[src]", "abs:src")

      allLinks.filter(_.endsWith(".html")).toSet
    } catch {
      case _: Exception => Set()
    }
  }

  /**
   * update map with links in the document with a certain attribute and tag
   */
  private def addExtensionToMap(doc: Document, cssQuery: String, attributeKey: String): Unit = {
    val links = doc.select(cssQuery).asScala.map(_.attr(attributeKey)).filter(link => link.startsWith(basePath))
    val filter = links.filter(!urlMapNonHtml.contains(_)).map(g => {
      urlMapNonHtml.put(g, 1)
      g.toLowerCase().substring(g.lastIndexOf(".")+1)
    })
    filter.foreach(l => {
      if extMap.contains(l) then
        extMap.update(l, extMap(l) + 1)
      else
        extMap.put(l, 1)
    })
  }

  /**
   * create WebStats object from the map of links
   */
  private def createWebStats(links: Set[String]) : WebStats = {
    val mapExtensions_ = extMap.toMap + ("html" -> links.size)
    WebStats(links.size + extMap.valuesIterator.sum, extMap.keySet.size, mapExtensions_, numWords.get())
  }

}
