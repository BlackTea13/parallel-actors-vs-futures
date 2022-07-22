import scala.annotation.tailrec
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import concurrent.ExecutionContext.Implicits.global

object MiscMethods {

  def timed(F: String => Any, startLink: String) = {
    val start = System.nanoTime()
    val result = F(startLink)
    val end = System.nanoTime()
    (result, (end - start)/1e9d)
  }

  def isWord(word: String): Boolean = {
    word.head.isLetter
      && word.count(_ == '-') <= 2
      && word.filterNot(_ == '-').forall(_.isLetter)
  }
  
  
}
