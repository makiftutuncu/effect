package effect

import java.util.concurrent.{CountDownLatch, TimeUnit}

class EffectUninterruptibleTest extends TestSuite {
  test("makes an effect uninterruptible so even if it gets interrupt call, it finishes its work first") {
    val (counter, effect) = counterIncrementingEffect
    val latch             = new CountDownLatch(1)

    assertEffectTakesAboutMillis(100) {
      for {
        fiber <- effect.delayed(10).repeat(10).uninterruptible.ensuring(Effect(latch.countDown())).fork
        _     <- Effect.unit.delayed(50)
        _     <- fiber.interrupt
      } yield ()
    }.assertValue(())
    latch.await(150, TimeUnit.MILLISECONDS)
    assertEquals(counter.get, 10)
  }
}
