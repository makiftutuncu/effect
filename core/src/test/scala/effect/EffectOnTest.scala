package effect

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

class EffectOnTest extends TestSuite with Tracing {
  // FIXME: `on` doesn't work as expected at the moment
  test("`on` shifts execution to a different execution context".ignore) {
    val executor = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
    val effect = for {
      first  <- Effect(Thread.currentThread.getName)
      second <- Effect(Thread.currentThread.getName).on(executor)
      third  <- Effect(Thread.currentThread.getName)
    } yield {
      println((first, second, third))
      (first, second, third)
    }

    assertEffect(effect) { case Result.Value((first, second, third)) =>
      assertNotEquals(first, second)
      assertNotEquals(second, third)
      assertEquals(first, third)
    }
  }
}
