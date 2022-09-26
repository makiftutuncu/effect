package effect

import effect.Result.Interrupted

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.ExecutionContext

class EffectRunTest extends TestSuite {
  test("creating an effect suspends execution") {
    val (counter, _) = counterIncrementingEffect
    assertEquals(counter.get(), 0)
  }

  test("running an effect that succeeds yields computed value") {
    val (counter, effect) = counterIncrementingEffect
    effect.assertValue(1)
    assertEquals(counter.get(), 1)
  }

  test("running an effect that fails yields error") {
    errorEffect.assertError(e)
  }

  test("running an effect that gets interrupted yields nothing") {
    val counter = getCounter
    val effect =
      for {
        fiber <- Effect {
          val value = counter.incrementAndGet()
          Thread.sleep(1000)
          value
        }.fork
        _ <- Effect.unit.delayed(100)
        _ <- fiber.interrupt
      } yield ()

    assertEffectTakesMillisBetween(100, 1000)(effect)
    assertEquals(counter.get(), 1)
  }

  test("running an effect that fails unexpectedly yields the unexpected error") {
    val counter = getCounter
    val effect = Effect {
      counter.incrementAndGet()
      throw exception
    }

    effect.assertUnexpectedError(exception)
    assertEquals(counter.get(), 1)
  }
}
