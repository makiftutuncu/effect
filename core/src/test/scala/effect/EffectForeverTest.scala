package effect

class EffectForeverTest extends TestSuite {
  test("repeats the effect forever as long as it is successful, ignoring previous result") {
    val (counter1, effect1) = counterIncrementingEffect

    assertEffectTakesAboutMillis(100) {
      for {
        fiber <- effect1.delayed(10).forever.fork
        _     <- Effect.unit.delayed(100)
        _     <- fiber.interrupt
      } yield ()
    }.assertValue(())
    assert(counter1.get() <= 10, s"Counter value was: ${counter1.get()}")

    val counter2 = getCounter

    assertEffectTakesAboutMillis(100) {
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
    }.assertValue(())
    assertEquals(counter2.get(), 6)
  }
}
