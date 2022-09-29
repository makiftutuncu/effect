/*
package effect

import effect.Result.Interrupted

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.ExecutionContext

class EffectTest extends TestSuite {
  test("`foldEffect` handles all cases of an effect to run given effect(s)") {
    helloEffect
      .foldEffect(
        {
          case Left(throwable) => Effect(s"throwable: $throwable")
          case Right(error)    => Effect(s"error: $error")
        },
        value => Effect(s"value: $value")
      )
      .assertValue("value: hello")

    helloEffect
      .foldEffect(
        {
          case Left(throwable) => Effect(s"throwable: $throwable")
          case Right(error)    => Effect(s"error: $error")
        },
        value => errorEffect
      )
      .assertError(e)

    helloEffect
      .foldEffect(
        {
          case Left(throwable) => Effect(s"throwable: $throwable")
          case Right(error)    => Effect(s"error: $error")
        },
        value => Effect.unexpectedError(exception)
      )
      .assertUnexpectedError(exception)

    errorEffect
      .foldEffect(
        {
          case Left(throwable) => Effect(s"throwable: $throwable")
          case Right(error)    => Effect(s"error: $error")
        },
        value => Effect(s"value: $value")
      )
      .assertValue(s"error: $e")

    errorEffect
      .foldEffect(
        {
          case Left(throwable) => Effect(s"throwable: $throwable")
          case Right(error)    => Effect.error(e.withCode(1))
        },
        value => Effect(s"value: $value")
      )
      .assertError(e.withCode(1))

    errorEffect
      .foldEffect(
        {
          case Left(throwable) => Effect(s"throwable: $throwable")
          case Right(error)    => Effect.unexpectedError(exception)
        },
        value => Effect(s"value: $value")
      )
      .assertUnexpectedError(exception)

    unexpectedErrorEffect
      .foldEffect(
        {
          case Left(throwable) => Effect(s"throwable: $throwable")
          case Right(error)    => Effect(s"error: $error")
        },
        value => Effect(s"value: $value")
      )
      .assertValue(s"throwable: $exception")

    unexpectedErrorEffect
      .foldEffect(
        {
          case Left(throwable) => errorEffect
          case Right(error)    => Effect(s"error: $error")
        },
        value => Effect(s"value: $value")
      )
      .assertError(e)

    unexpectedErrorEffect
      .foldEffect(
        {
          case Left(throwable) => Effect.unexpectedError(exception2)
          case Right(error)    => Effect(s"error: $error")
        },
        value => Effect(s"value: $value")
      )
      .assertUnexpectedError(exception2)
  }

  test("`fold` handles all cases of an effect to compute a value") {
    helloEffect
      .fold(
        {
          case Left(throwable) => s"throwable: $throwable"
          case Right(error)    => s"error: $error"
        },
        value => s"value: $value"
      )
      .assertValue("value: hello")

    errorEffect
      .fold(
        {
          case Left(throwable) => s"throwable: $throwable"
          case Right(error)    => s"error: $error"
        },
        value => s"value: $value"
      )
      .assertValue(s"error: $e")

    unexpectedErrorEffect
      .fold(
        {
          case Left(throwable) => s"throwable: $throwable"
          case Right(error)    => s"error: $error"
        },
        value => s"value: $value"
      )
      .assertValue(s"throwable: $exception")
  }

  test("`handleAllErrorsEffect` handles error and unexpected error cases of an effect to run given effect") {
    helloEffect
      .handleAllErrorsEffect {
        case Left(throwable) => Effect(s"throwable: $throwable")
        case Right(error)    => Effect(s"error: $error")
      }
      .assertValue("hello")

    errorEffect
      .handleAllErrorsEffect {
        case Left(throwable) => Effect(s"throwable: $throwable")
        case Right(error)    => Effect(s"error: $error")
      }
      .assertValue(s"error: $e")

    errorEffect
      .handleAllErrorsEffect {
        case Left(throwable) => Effect(s"throwable: $throwable")
        case Right(error)    => Effect.error(e.withCode(1))
      }
      .assertError(e.withCode(1))

    errorEffect
      .handleAllErrorsEffect {
        case Left(throwable) => Effect(s"throwable: $throwable")
        case Right(error)    => Effect.unexpectedError(exception)
      }
      .assertUnexpectedError(exception)

    unexpectedErrorEffect
      .handleAllErrorsEffect {
        case Left(throwable) => Effect(s"throwable: $throwable")
        case Right(error)    => Effect(s"error: $error")
      }
      .assertValue(s"throwable: $exception")

    unexpectedErrorEffect
      .handleAllErrorsEffect {
        case Left(throwable) => errorEffect
        case Right(error)    => Effect(s"error: $error")
      }
      .assertError(e)

    unexpectedErrorEffect
      .handleAllErrorsEffect {
        case Left(throwable) => Effect.unexpectedError(exception2)
        case Right(error)    => Effect(s"error: $error")
      }
      .assertUnexpectedError(exception2)
  }

  test("`handleAllErrors` handles error and unexpected error cases of an effect to compute a value") {
    helloEffect
      .handleAllErrors {
        case Left(throwable) => s"throwable: $throwable"
        case Right(error)    => s"error: $error"
      }
      .assertValue("hello")

    errorEffect
      .handleAllErrors {
        case Left(throwable) => s"throwable: $throwable"
        case Right(error)    => s"error: $error"
      }
      .assertValue(s"error: $e")

    unexpectedErrorEffect
      .handleAllErrors {
        case Left(throwable) => s"throwable: $throwable"
        case Right(error)    => s"error: $error"
      }
      .assertValue(s"throwable: $exception")
  }

  test("`handleUnexpectedErrorEffect` handles unexpected error case of an effect to run given effect") {
    helloEffect
      .handleUnexpectedErrorEffect { throwable => Effect(s"throwable: $throwable") }
      .assertValue("hello")

    errorEffect
      .handleUnexpectedErrorEffect { throwable => Effect(s"throwable: $throwable") }
      .assertError(e)

    unexpectedErrorEffect
      .handleUnexpectedErrorEffect { throwable => Effect(s"throwable: $throwable") }
      .assertValue(s"throwable: $exception")

    unexpectedErrorEffect
      .handleUnexpectedErrorEffect { throwable => errorEffect }
      .assertError(e)

    unexpectedErrorEffect
      .handleUnexpectedErrorEffect { throwable => Effect.unexpectedError(exception2) }
      .assertUnexpectedError(exception2)
  }

  test("`handleUnexpectedError` handles unexpected error case of an effect to compute a value") {
    helloEffect
      .handleUnexpectedError { throwable => s"throwable: $throwable" }
      .assertValue("hello")

    errorEffect
      .handleUnexpectedError { throwable => s"throwable: $throwable" }
      .assertError(e)

    unexpectedErrorEffect
      .handleUnexpectedError { throwable => s"throwable: $throwable" }
      .assertValue(s"throwable: $exception")
  }

  test("`handleErrorEffect` handles error case of an effect to run given effect") {
    helloEffect
      .handleErrorEffect { e => Effect(s"error: $e") }
      .assertValue("hello")

    errorEffect
      .handleErrorEffect { e => Effect(s"error: $e") }
      .assertValue(s"error: $e")

    errorEffect
      .handleErrorEffect { e => Effect.error(e.withCode(1)) }
      .assertError(e.withCode(1))

    errorEffect
      .handleErrorEffect { e => Effect.unexpectedError(exception2) }
      .assertUnexpectedError(exception2)

    unexpectedErrorEffect
      .handleErrorEffect { e => Effect(s"error: $e") }
      .assertUnexpectedError(exception)
  }

  test("`handleError` handles error case of an effect to compute a value") {
    helloEffect
      .handleError { e => s"error: $e" }
      .assertValue("hello")

    errorEffect
      .handleError { e => s"error: $e" }
      .assertValue(s"error: $e")

    unexpectedErrorEffect
      .handleError { e => s"error: $e" }
      .assertUnexpectedError(exception)
  }

  test("`mapError` handles error case of an effect to modify the error") {
    helloEffect
      .mapError(_.withCode(1))
      .assertValue("hello")

    errorEffect
      .mapError(_.withCode(1))
      .assertError(e.withCode(1))

    unexpectedErrorEffect
      .mapError(_.withCode(1))
      .assertUnexpectedError(exception)
  }

  test("`mapUnexpectedError` handles unexpected error case of an effect to modify the unexpected error") {
    helloEffect
      .mapUnexpectedError(_.withSuppressed(exception2))
      .assertValue("hello")

    errorEffect
      .mapUnexpectedError(_.withSuppressed(exception2))
      .assertError(e)

    unexpectedErrorEffect
      .mapUnexpectedError(_.withSuppressed(exception2))
      .assertUnexpectedError(exception.withSuppressed(exception2))
  }

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
