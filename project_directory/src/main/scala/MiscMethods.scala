import scala.annotation.tailrec
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import concurrent.ExecutionContext.Implicits.global

object MiscMethods {

  def timed(F: String => WebStats, startLink: String) = {
    val start = System.nanoTime()
    val result = F(startLink)
    val end = System.nanoTime()
    (end - start)/1e9d
  }

  def isWord(word: String): Boolean = {
    word.head.isLetter
      && word.count(_ == '-') <= 2
      && word.filterNot(_ == '-').forall(_.isLetter)
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
  
}
