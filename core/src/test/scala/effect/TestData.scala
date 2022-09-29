package effect

import java.util.concurrent.atomic.AtomicInteger

trait TestData {
  val e: E = E("test")

  val exception: Exception = Exception("test")

  val helloEffect: Effect[String] = Effect("hello")

  val worldEffect: Effect[String] = Effect("world")

  val errorEffect: Effect[Nothing] = Effect.error(e)

  val unexpectedErrorEffect: Effect[Nothing] = Effect.unexpectedError(exception)

  val interruptedEffect: Effect[Nothing] = Effect.callback(callback => callback(Result.Interrupted))

  def counterIncrementingEffect: (AtomicInteger, Effect[Int]) = {
    val counter = getCounter
    counter -> Effect(counter.incrementAndGet())
  }

  def getCounter: AtomicInteger = AtomicInteger(0)

  val appendWorld: String => String = s => s"$s world"
}
