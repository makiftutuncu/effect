package effect

import effect.Result
import munit.{FunSuite, Location}

import scala.concurrent.Future

trait TestSuite extends FunSuite {
  protected val traceEnabled: Boolean = false

  val e: E = E("test")

  def assertEffect[A](effect: Effect[A])(assertion: PartialFunction[Result[A], Unit])(using Location): Unit = {
    val result = effect.unsafeRun(traceEnabled = traceEnabled)
    if (assertion.isDefinedAt(result)) assertion(result) else fail(s"Unmatched result: $result")
  }

  def time[A](a: => A): (A, Long) = {
    val before = System.currentTimeMillis()
    val result = a
    val after  = System.currentTimeMillis()
    result -> (after - before)
  }

  def assertTakesMillisBetween[A](min: Long, max: Long)(action: => A)(using Location): A = {
    assert(min >= 0, s"Min $min must be positive")
    assert(min < max, s"Min $min must be less than max $max")
    val (result, elapsed) = time(action)
    assertEquals(elapsed >= min && elapsed <= max, true, s"Expected to take between $min ms and $max ms but took $elapsed ms")
    result
  }

  def assertEffectTakesMillisBetween[A](min: Long, max: Long)(effect: => Effect[A])(using Location): Result[A] =
    assertTakesMillisBetween(min, max)(effect.unsafeRun(traceEnabled = traceEnabled))

  extension (t: Throwable)
    def withSuppressed(other: Throwable): Throwable = {
      t.addSuppressed(other)
      t
    }

  extension [A](effect: Effect[A]) {
    def assertResult(expected: => Result[A])(using Location): Unit =
      assertEffect(effect)(result => assertEquals(result, expected))

    def assertValue(expected: => A)(using Location): Unit =
      assertEffect(effect) { case Result.Value(value) =>
        assertEquals(value, expected)
      }

    def assertError(expected: => E)(using Location): Unit =
      assertEffect(effect) { case Result.Error(e) =>
        assertEquals(e, expected)
      }

    def assertUnexpectedError(expected: => Throwable)(using Location): Unit =
      assertEffect(effect) { case Result.UnexpectedError(throwable) =>
        assertEquals(throwable, expected)
      }
  }
}
