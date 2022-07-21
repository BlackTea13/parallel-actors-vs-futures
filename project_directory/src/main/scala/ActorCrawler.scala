import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, DeadLetter, Props}
import akka.event.Logging
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import concurrent.ExecutionContext.Implicits.global
import java.net.{URI, URL}
import java.util.concurrent.{ConcurrentLinkedQueue, ConcurrentSkipListSet}
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import collection.JavaConverters.*
import scala.annotation.tailrec
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._



object ActorCrawler {
  private val numFiles: AtomicInteger = AtomicInteger(0)
  private val numWords: AtomicInteger = AtomicInteger(0)
  private val extMap: mutable.Map[String, Int] = TrieMap[String, Int]()
  private val urlMapNonHtml: mutable.Map[String, Int] = TrieMap[String, Int]()
  private var basePath: String = ""

  def crawlForStats(basePath: String) = {
    this.basePath = basePath

    import Master._
    val system = ActorSystem("system")
    val master = system.actorOf(Props(new Master), "robMaster")
    implicit val timeout: Timeout = Timeout(100.seconds)
    master ! Start
  }

  private def createWebStats(links: Set[String]) : WebStats = {
    val mapExtensions_ = extMap.toMap + ("html" -> links.size)
    WebStats(links.size, extMap.keySet.size, mapExtensions_, numWords.get())
  }


  object Master {
    case object Start
    case class LinkRequest(ref: ActorRef)
    case class NewUrls(pages: Set[String])
    case object Complete
  }
  class Master extends Actor with ActorLogging {
    import Master._
    import Worker._

    val visited = mutable.Set[String]()
    val pending = mutable.Queue[String]()

    val logger = Logging(context.system, this)
    val numWorkers = 32
    val workers = Array.fill(numWorkers) { context.actorOf(Props(new Worker))}

    var originalSender: ActorRef = ActorRef.noSender
    override def receive: Receive = {
      case Start =>
        originalSender = sender()
        pending += basePath
        workers.foreach(_ ! WorkToDo)

      case LinkRequest(ref) =>
        if pending.nonEmpty then
          val page = pending.dequeue()
          visited += page
          ref ! ProcessPage(page)

      case NewUrls(pages) =>
        val newLinks = pages.filter(page => !(visited.contains(page) || pending.contains(page)))
        pending ++= newLinks
        workers.foreach(_ ! WorkToDo)
        logger.info("received {}", newLinks.size)
        p += newLinks.size

      case Complete =>
        println(createWebStats(visited.toSet))
    }
  }

  var p = 0

  object Worker {
    case object WorkToDo
    case class ProcessPage(link: String)
  }
  class Worker extends Actor{
    import Master._
    import Worker._
    val logger = Logging(context.system, this)

    override def receive: Receive = {
      case WorkToDo =>
        sender() ! LinkRequest(context.self)

      case ProcessPage(link) =>
        val links = extractLinks(link)
        sender() ! NewUrls(links)
    }

    def extractLinks(link: String) : Set[String] = {
      try {
        val doc = Jsoup.connect(link).get
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
        case _ : Exception => Set()
      }
    }

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
  }


  
}