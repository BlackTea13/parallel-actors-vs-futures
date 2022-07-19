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
  
}
