import FutureCrawler.{addExtensionToMap, basePath, crawlForStats, numWords}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import java.net.{URI, URL}
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import collection.JavaConverters.*



object ActorCrawler {
  private val numFiles: AtomicInteger = AtomicInteger(0)
  private val numWords: AtomicInteger = AtomicInteger(0)
  private val extMap: mutable.Map[String, Int] = TrieMap[String, Int]()
  private val urlMapNonHtml: mutable.Map[String, Int] = TrieMap[String, Int]()
  private var basePath: String = ""
  private val system = ActorSystem("system")


  def crawlForStats(basePath: String): WebStats = {
    this.basePath = basePath

    WebStats(0, 0, Map(), 0)
  }

  class Master extends Actor {
    override def receive: Receive = {
      ???
    }
  }

  object statsUpdater {
    case class UpdateStats(link: String, cssQuery: String, attr: String)
  }
  
  class statsUpdater extends Actor {
    override def receive: Receive = ???
  }


  object LinkExtractor {
    case class ExtractLinks(link: String)
    case class LinkReply(links: Set[String])
  }
  class LinkExtractor extends Actor {
    import LinkExtractor._

    var allLinks: Set[String] = Set()
    val NUM_CHILD = 8
    override def receive: Receive = {
      case ExtractLinks(path) =>
        allLinks = extractLinks(path)
      case LinkReply =>
        sender() ! LinkReply(allLinks)
    }

    def extractLinks(path: String): Set[String] = {
      try {
        val doc = Jsoup.connect(path).get
        val allLinks = doc.select("a[href]").asScala.map(link => {
          try {
            val uri = URL(link.attr("abs:href")).toURI
            if uri.getFragment != null
            then URI(uri.getScheme, uri.getSchemeSpecificPart, null).toURL.toString
            else link.attr("abs:href")
          } catch {
            case _: Exception => ""
          }
        }).filter(_.startsWith(basePath))
        numWords.addAndGet(doc.text().split("\\s+").count(MiscMethods.isWord))
        allLinks.filter(_.endsWith(".html")).toSet
      } catch {
        case _: Exception => Set()
      }
    }
  }




}
