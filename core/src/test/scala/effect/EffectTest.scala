/*
package effect

import effect.Result.Interrupted

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.ExecutionContext

class EffectTest extends TestSuite {
  test("`ensuring` makes sure given effect is run when the effect is completed in any way") {
    val counter1 = getCounter
    helloEffect.ensuring(Effect(counter1.incrementAndGet())).assertValue("hello")
    assertEquals(counter1.get(), 1)

    val counter2 = getCounter
    errorEffect.ensuring(Effect(counter2.incrementAndGet())).assertError(e)
    assertEquals(counter2.get(), 1)

    val counter3 = getCounter
    Effect.unexpectedError(exception).ensuring(Effect(counter3.incrementAndGet())).assertUnexpectedError(exception)
    assertEquals(counter3.get(), 1)

    // TODO: `ensuring` doesn't work properly with interruptions
    val counter4 = getCounter
    val effect = for {
      fiber <- Effect.unit.delayed(1000).ensuring(Effect(counter4.incrementAndGet())).fork
      _     <- Effect.unit.delayed(100)
      _     <- fiber.interrupt
    } yield ()
    assertEffectTakesMillisBetween(100, 150)(effect)
    assertEquals(counter4.get(), 1)
  }

  // TODO: Test is flaky
  test("`on` shifts execution to a different execution context") {
    val executor = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
    val effect = for {
      first  <- Effect(Thread.currentThread().getName)
      second <- Effect(Thread.currentThread().getName).on(executor)
      third  <- Effect(Thread.currentThread().getName)
    } yield (first, second, third)

    assertEffect(effect) { case Result.Value((first, second, third)) =>
      assertNotEquals(first, second)
      assertEquals(first, third)
    }
  }

  test("`repeat` repeats the effect given number of times as long as it is successful, ignoring previous result") {
    val counter1 = getCounter

    val result1 = assertEffectTakesMillisBetween(300, 350) {
      Effect(counter1.incrementAndGet())
        .delayed(100)
        .repeat(3)
    }

    assertEquals(result1, Result.Value(()))
    assertEquals(counter1.get(), 3)

    val counter2 = getCounter

    val result2 = assertEffectTakesMillisBetween(100, 150) {
      Effect {
        val value = counter2.incrementAndGet()
        if (value > 1) {
          throw exception
        } else {
          Thread.sleep(100)
          value
        }
      }
        .repeat(3)
    }

    assertEquals(result2, Result.UnexpectedError(exception))
    assertEquals(counter2.get(), 2)
  }

  test("`forever` repeats the effect forever as long as it is successful, ignoring previous result") {
    val counter1 = getCounter

    assertEffectTakesMillisBetween(100, 150) {
      for {
        fiber <- Effect(counter1.incrementAndGet()).delayed(10).forever.fork
        _     <- Effect.unit.delayed(100)
        _     <- fiber.interrupt
      } yield ()
    }

    assert(counter1.get() <= 10, s"Counter value was: ${counter1.get()}")

    val counter2 = getCounter

    assertEffectTakesMillisBetween(100, 150) {
      for {
        fiber <- Effect {
          val value = counter2.incrementAndGet()
          if (value > 5) {
            throw exception
          } else {
            Thread.sleep(10)
            value
          }
        }.forever.fork
        _ <- Effect.unit.delayed(100)
        _ <- fiber.interrupt
      } yield ()
    }

    assertEquals(counter2.get(), 6)
  }

  // TODO: Fix this
  test("`uninterruptible` makes an effect uninterruptible so even if it gets interrupt call, it finishes its work first") {
    val counter = getCounter

    assertEffectTakesMillisBetween(100, 150) {
      for {
        fiber <- Effect(counter.incrementAndGet()).delayed(10).repeat(10).uninterruptible.fork
        _     <- Effect.unit.delayed(50)
        _     <- fiber.interrupt
      } yield ()
    }

    assertEquals(counter.get(), 10)
  }
}
 */
