package effect

import java.util.concurrent.atomic.AtomicInteger

class EffectTest extends TestSuite {
  test("creating an effect suspends execution") {
    val counter = AtomicInteger(0)
    val effect  = Effect(counter.incrementAndGet())

    assertEquals(counter.get(), 0)
  }

  test("running an effect that succeeds yields computed value") {
    val counter = AtomicInteger(0)
    val effect  = Effect(counter.incrementAndGet())

    effect.assertValue(1)
    assertEquals(counter.get(), 1)
  }

  test("running an effect that fails yields error") {
    Effect.error(E("test")).assertError(E("test"))
  }

  test("running an effect that gets interrupted yields nothing") {
    val counter = AtomicInteger(0)
    val effect =
      for {
        fiber <- Effect {
          val value = counter.incrementAndGet()
          Thread.sleep(1000)
          value
        }.fork
        _ <- Effect(Thread.sleep(100))
        _ <- fiber.interrupt
      } yield ()

    assertTakesMillisBetween(100L, 1000L) {
      effect.unsafeRun()
    }
    assertEquals(counter.get(), 1)
  }

  test("running an effect that fails unexpectedly yields the unexpected error") {
    val counter   = AtomicInteger(0)
    val exception = Exception("test")
    val effect = Effect {
      counter.incrementAndGet()
      throw exception
    }

    effect.assertUnexpectedError(exception)
    assertEquals(counter.get(), 1)
  }
}
