import akka.actor.Status.{Failure, Success}
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, DeadLetter, PoisonPill, Props, Timers}
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
import scala.concurrent.duration.*

object ActorCrawler {
  private val numFiles: AtomicInteger = AtomicInteger(0)
  private val numWords: AtomicInteger = AtomicInteger(0)
  private val extMap: mutable.Map[String, Int] = TrieMap[String, Int]()
  private val urlMapNonHtml: mutable.Map[String, Int] = TrieMap[String, Int]()
  private var basePath: String = ""
  private var start = 0L
  private var end = 0L
  

  case object TimerKey
  case class StartCrawl(basePath: String)
  class test extends Actor with Timers with ActorLogging {
    import Master._

    var ws: WebStats = WebStats(0, 0, Map(), 0)
    var requester: ActorRef = null
    val master: ActorRef = context.actorOf(Props(classOf[Master]), "MasterCrawler")
    override def receive: Receive = {
      case StartCrawl(path) =>
        requester = sender()
        basePath = path
        timers.startPeriodicTimer(TimerKey, "check", 1.seconds)
        master ! Start
      case "check" =>
        master ! CompletionCheck
      case Completed(ws) =>
        log.info("Crawl completed!")
        requester ! ws
        timers.cancel(TimerKey)
        context.stop(self)
    }
  }

  object Master {
    case object Start
    case object LinkRequest
    case class NewUrls(pages: Set[String])
    case object CompletionCheck
    case class Completed(ws: WebStats)
  }
  class Master extends Actor with ActorLogging {
    import Master._
    import Worker._

    var requester: Option[ActorRef] = None
    val visited: mutable.Set[String] = mutable.Set[String]()
    val pending: mutable.Queue[String] = mutable.Queue[String]()
    val numWorkers = 24
    val workers: Array[ActorRef] = Array.fill(numWorkers) { context.actorOf(Props(classOf[Worker]))}
    var completeCheckPasses = 0
    override def receive: Receive = {
      case Start =>
        requester = Some(sender())
        try {
          Jsoup.connect(basePath)
          pending += basePath
          workers.foreach(_ ! WorkToDo)
        } catch {
          case e : Exception =>
            log.error("error encountered, could not connect to seed link")
            sender() ! Failure(e)
        }
        
      case LinkRequest =>
        if pending.nonEmpty then
          val page = pending.dequeue()
          visited += page
          sender() ! ProcessPage(page)

      case NewUrls(pages) =>
        val newLinks = pages.filter(page => !(visited.contains(page) || pending.contains(page)))
        pending ++= newLinks
        workers.foreach(_ ! WorkToDo)

        /*
        This is definitely not the way to do it but I didn't have enough time
        to learn the way I think you're supposed to do it with the "Ask Pattern"
        */
      case CompletionCheck =>
        if pending.isEmpty then
          if completeCheckPasses + 1 == 5 then
            val ws : WebStats = createWebStats(visited.toSet)
            requester.get ! Completed(ws)
            context.stop(self)
          else
            completeCheckPasses += 1
        else
          completeCheckPasses = 0
    }
  }
  object Worker {
    case object WorkToDo
    case class ProcessPage(link: String)
  }
  class Worker extends Actor with ActorLogging {
    import Master._
    import Worker._
    override def receive: Receive = {
      case WorkToDo =>
        sender() ! LinkRequest

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

  private def createWebStats(links: Set[String]) : WebStats = {
    val mapExtensions_ = extMap.toMap + ("html" -> links.size)
    WebStats(links.size + extMap.valuesIterator.sum, extMap.keySet.size, mapExtensions_, numWords.get())
  }
}