import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
  * Cross-thread stack traces
  *
  * This implicit class adds the `withTrace` method to `Future`. Calling `withTrace` grabs the stack trace before
  * entering the future context, then if the future fails it combines the stack trace of the future thread with the
  * trace of the original thread. This results in a much more detailed stack trace that crosses thread boundaries.
  * Additionally, `withTrace` is cumulative in the sense that you can call it repeatedly and generate a stack trace
  * for multiple threads.
  *
  * Example:
  * --------
  *
  * import FutureTrace._
  * object Test {
  *   implicit val ec = scala.concurrent.ExecutionContext.global
  *
  *   def futureFunc(str: String): Future[String] = {
  *     Future {
  *       if (str == "a") "ok"
  *       else throw new Exception("error")
  *     }.withTrace
  *   }
  *
  *   def f1 = futureFunc("a")
  *   def f2 = futureFunc("b")
  *
  *   def main(args: Array[String]): Unit = {
  *     Await.result(f2, Duration.Inf)
  *   }
  * }
  *
  * This yields the following trace:
  *
  * Exception in thread "main" java.lang.Exception: error
  * 	at Test$.$anonfun$futureFunc$1
  * 	...
  * 	at Original stack trace:.----------(:0)
  * 	at Test$.futureFunc
  * 	at Test$.f2
  * 	at Test$.main
  * 	at Test.main
  *
  * Example 2, multiple futures:
  * ----------------------------
  *
  * import FutureTrace._
  * object Test {
  *   implicit val ec = scala.concurrent.ExecutionContext.global
  *
  *   def futureFunc(str: String): Future[String] = {
  *     Future {
  *       if (str == "a") "ok"
  *       else throw new Exception("error")
  *     }
  *   }
  *
  *   def future2(): Future[String] = {
  *     val f = for {
  *       x <- futureFunc("a").withTrace
  *       y <- futureFunc("b").withTrace
  *     } yield x + y
  *     f.withTrace
  *   }
  *
  *   def main(args: Array[String]): Unit = {
  *     Await.result(future2(), Duration.Inf)
  *   }
  * }
  *
  * Yields the following trace:
  *
  * Exception in thread "main" java.lang.Exception: error
  * 	at Test$.$anonfun$futureFunc$3
  * 	...
  * 	at Original stack trace:.----------(:0)
  * 	at Test$.$anonfun$future2$1
  * 	...
  * 	at Original stack trace:.----------(:0)
  * 	at Test$.future2
  * 	at Test$.main
  * 	at Test.main
  * */
object FutureTrace {
  implicit class FutureWithTrace[T](future: Future[T]) {
    def withTrace(implicit ec: ExecutionContext): Future[T] = {
      val trace = Thread.currentThread().getStackTrace.drop(2)
      future.recover {
        case NonFatal(t) =>
          t.setStackTrace(Array.concat(
            t.getStackTrace,
            Array(new StackTraceElement("Original stack trace:", "----------", "", 0)),
            trace))
          throw t
      }
    }
  }
}
