package effect

import e.scala.E

import java.util.concurrent.atomic.AtomicInteger

trait TestData {
  val e: E = E.name("test")

  val exception: Exception = Exception("test")

  val helloEffect: Effect[String] = Effect("hello")

  val worldEffect: Effect[String] = Effect("world")

  val errorEffect: Effect[Nothing] = Effect.error(e)

  val unexpectedErrorEffect: Effect[Nothing] = Effect.unexpectedError(exception)

  val interruptedEffect: Effect[Nothing] = Effect.callback(complete => complete(Result.Interrupted))

  def counterIncrementingEffect: (AtomicInteger, Effect[Int]) = {
    val counter = getCounter
    counter -> Effect(counter.incrementAndGet())
  }

  def getCounter: AtomicInteger = AtomicInteger(0)

  val appendWorld: String => String = s => s"$s world"

  val errorHandler: E => String = e => s"error: $e"

  val errorHandlerEffect: E => Effect[String] = e => Effect(errorHandler(e))

  val unexpectedErrorHandler: Throwable => String = throwable => s"unexpected error: $throwable"

  val unexpectedErrorHandlerEffect: Throwable => Effect[String] = throwable => Effect(unexpectedErrorHandler(throwable))

  val allErrorsHandler: Either[Throwable, E] => String = {
    case Left(throwable) => unexpectedErrorHandler(throwable)
    case Right(e)        => errorHandler(e)
  }

  val allErrorsHandlerEffect: Either[Throwable, E] => Effect[String] = either => Effect(allErrorsHandler(either))

  def handler[A]: Result[A] => String = {
    case Result.Value(value)               => s"value: $value"
    case Result.Error(e)                   => errorHandler(e)
    case Result.UnexpectedError(throwable) => unexpectedErrorHandler(throwable)
    case Result.Interrupted                => "interrupted"
  }

  def handlerEffect[A]: Result[A] => Effect[String] = result => Effect(handler(result))
}
