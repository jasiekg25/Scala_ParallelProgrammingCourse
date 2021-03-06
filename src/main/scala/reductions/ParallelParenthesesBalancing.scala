package reductions

import scala.annotation._
import org.scalameter._

object ParallelParenthesesBalancingRunner {

  @volatile var seqResult = false

  @volatile var parResult = false

  val standardConfig = config(
    Key.exec.minWarmupRuns -> 40,
    Key.exec.maxWarmupRuns -> 80,
    Key.exec.benchRuns -> 120,
    Key.verbose -> true
  ) withWarmer(new Warmer.Default)

  def main(args: Array[String]): Unit = {
    val length = 100000000
    val chars = new Array[Char](length)
    val threshold = 10000
    val seqtime = standardConfig measure {
      seqResult = ParallelParenthesesBalancing.balance(chars)
    }
    println(s"sequential result = $seqResult")
    println(s"sequential balancing time: $seqtime")

    val fjtime = standardConfig measure {
      parResult = ParallelParenthesesBalancing.parBalance(chars, threshold)
    }
    println(s"parallel result = $parResult")
    println(s"parallel balancing time: $fjtime")
    println(s"speedup: ${seqtime.value / fjtime.value}")
  }
}

object ParallelParenthesesBalancing extends ParallelParenthesesBalancingInterface {

  /** Returns `true` iff the parentheses in the input `chars` are balanced.
   */
  def balance(chars: Array[Char]): Boolean = {
    def code(ch: Char): Int = ch match {
      case '(' => 1
      case ')' => -1
      case _ => 0
    }
    @tailrec
    def loop(chLs: List[Char], acc: Int = 0): Int = chLs match {
      case head::tail if acc >= 0 => loop(tail, acc + code(head))
      case _ => acc
    }
    loop(chars.toList) == 0
  }


  /** Returns `true` iff the parentheses in the input `chars` are balanced.
   */
  def parBalance(chars: Array[Char], threshold: Int): Boolean = {

    def traverse(idx: Int, until: Int, mainCounter: Int, additionalCounter: Int): (Int, Int) = {
      if (idx < until) {
        chars(idx) match {
          case '(' => traverse(idx + 1, until, mainCounter + 1, additionalCounter)
          case ')' =>
            if (mainCounter > 0) traverse(idx + 1, until, mainCounter - 1, additionalCounter)
            else traverse(idx + 1, until, mainCounter, additionalCounter + 1)
          case _ => traverse(idx + 1, until, mainCounter, additionalCounter)
        }
      } else (mainCounter, additionalCounter)
    }

    def reduce(from: Int, until: Int): (Int, Int) = {
      val size = until - from
      if (size > threshold) {
        val halfSize = size / 2
        val ((a1, a2), (b1, b2)) = parallel(reduce(from, from + halfSize), reduce(from + halfSize, until))
        if (a1 > b2) {
          // )))((())(( => )))(((
          (a1 - b2 + b1) -> a2
        } else {
          // )))(()))(( => ))))((
          b1 -> (b2 - a1 + a2)
        }
      }
      else {
        traverse(from, until, 0, 0)
      }
    }

    reduce(0, chars.length) == (0, 0)
  }


  // For those who want more:
  // Prove that your reduction operator is associative!

}
