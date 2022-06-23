package effect

import munit.FunSuite

import java.util.concurrent.atomic.AtomicInteger

class EffectTest extends FunSuite {
  def time[A](a: => A): (A, Long) = {
    val before = System.currentTimeMillis()
    val result = a
    val after  = System.currentTimeMillis()
    result -> (after - before)
  }

  test("creating an effect suspends execution") {
    val counter = AtomicInteger(0)
    val effect  = Effect(counter.incrementAndGet())

    assertEquals(counter.get(), 0)
  }

  test("running an effect that succeeds yields computed value") {
    val counter = AtomicInteger(0)
    val effect  = Effect(counter.incrementAndGet())
    val result  = effect.unsafeRun()

    assertEquals(counter.get(), 1)
    assertEquals(result, Result.Value(1))
  }

  test("running an effect that fails yields error") {
    val effect = Effect.error(E("test"))
    val result = effect.unsafeRun()

    assertEquals(result, Result.Error(E("test")))
  }

  /*test("running an effect that gets interrupted yields nothing") {
    val counter = AtomicInteger(0)
    val effect =
      for {
        fiber <- Effect {
          val value = counter.incrementAndGet()
          Thread.sleep(10000000)
          value
        }.fork
        _     <- fiber.interrupt
        value <- fiber.join
      } yield value
    val (result, elapsed) = time(effect.unsafeRun(traceEnabled = true))

    assertEquals(counter.get(), 1)
    assertEquals(result, Result.Interrupted)
    assertEquals(elapsed <= 100, true, s"Expected to take less than 100 ms but took $elapsed ms")
  }*/

  test("running an effect that fails unexpectedly yields the unexpected error") {
    val counter   = AtomicInteger(0)
    val exception = Exception("test")
    val effect = Effect {
      val value = counter.incrementAndGet()
      throw exception
    }
    val result = effect.unsafeRun()

    assertEquals(counter.get(), 1)
    assertEquals(result, Result.UnexpectedError(exception))
  }
}
