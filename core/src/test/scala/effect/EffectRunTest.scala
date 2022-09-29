package effect

class EffectRunTest extends TestSuite {
  test("effect suspends execution until it is run") {
    val (counter, _) = counterIncrementingEffect
    assertEquals(counter.get(), 0)
  }

  test("effect that succeeds completes with the success value") {
    val (counter, effect) = counterIncrementingEffect
    effect.assertValue(1)
    assertEquals(counter.get(), 1)
  }

  test("effect that fails completes with the error") {
    errorEffect.assertError(e)
  }

  test("effect that gets interrupted completes with interruption") {
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

    assertEffectTakesAboutMillis(100)(effect)
    assertEquals(counter.get(), 1)
  }

  test("effect that fails unexpectedly completes with the unexpected error") {
    val counter = getCounter
    val effect = Effect {
      counter.incrementAndGet()
      throw exception
    }

    effect.assertUnexpectedError(exception)
    assertEquals(counter.get(), 1)
  }
}
