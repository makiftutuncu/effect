package effect

import effect.Result.Interrupted

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.ExecutionContext

class EffectTest extends TestSuite {
  val exception: Exception  = Exception("test")
  val exception2: Exception = Exception("test2")

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
        _ <- Effect.unit.delayed(100)
        _ <- fiber.interrupt
      } yield ()

    assertEffectTakesMillisBetween(100, 1000)(effect)
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

  test("`flatMap` sequences effects when they are successful") {
    Effect("hello").flatMap(h => Effect(h + " world")).assertValue("hello world")

    Effect("hello").flatMap(_ => Effect.error(e)).assertError(e)

    Effect.error(e).flatMap(_ => Effect("hello")).assertError(e)
  }

  test("`map` converts the value in effect when it is successful") {
    Effect("hello").map(_ + " world").assertValue("hello world")

    Effect.error(e).map(_ => "hello").assertError(e)
  }

  test("`as` replaces the value in effect when it is successful") {
    Effect("hello").as("world").assertValue("world")

    Effect.error(e).as("hello").assertError(e)
  }

  test("`fork` runs the effect in a separate fiber") {
    val effect = for {
      fiber1 <- Effect("hello").delayed(100).fork
      fiber2 <- Effect("world").delayed(100).fork
      hello  <- fiber1.join
      world  <- fiber2.join
    } yield s"$hello $world"

    val result = assertEffectTakesMillisBetween(100, 150)(effect)

    assertEquals(result, Result.Value("hello world"))
  }

  test("`zipEffect` runs both effects sequentially, when they are both successful, runs given effect with results") {
    val effect1 =
      Effect("hello").delayed(100).zipEffect(Effect("world").delayed(100)) { (h, w) =>
        Effect(s"$h $w")
      }

    assertEquals(assertEffectTakesMillisBetween(200, 250)(effect1), Result.Value("hello world"))

    val effect2 =
      Effect.error(e).zipEffect(Effect("world").delayed(100)) { (h, w) =>
        Effect(s"$h $w")
      }

    assertEquals(assertEffectTakesMillisBetween(0, 50)(effect2), Result.Error(e))

    val effect3 =
      Effect("hello").delayed(100).zipEffect(Effect.error(e)) { (h, w) =>
        Effect(s"$h $w")
      }

    assertEquals(assertEffectTakesMillisBetween(100, 150)(effect3), Result.Error(e))

    val effect4 =
      Effect("hello").delayed(100).zipEffect(Effect("world").delayed(100)) { (_, _) =>
        Effect.error(e)
      }

    assertEquals(assertEffectTakesMillisBetween(200, 250)(effect4), Result.Error(e))
  }

  test("`zipParEffect` runs both effects in parallel, when they are both successful, runs given effect with results") {
    val effect1 =
      Effect("hello").delayed(100).zipParEffect(Effect("world").delayed(100)) { (h, w) =>
        Effect(s"$h $w")
      }

    assertEquals(assertEffectTakesMillisBetween(100, 150)(effect1), Result.Value("hello world"))

    val effect2 =
      Effect.error(e).zipParEffect(Effect("world").delayed(100)) { (h, w) =>
        Effect(s"$h $w")
      }

    assertEquals(assertEffectTakesMillisBetween(100, 150)(effect2), Result.Error(e))

    val effect3 =
      Effect("hello").delayed(100).zipParEffect(Effect.error(e)) { (h, w) =>
        Effect(s"$h $w")
      }

    assertEquals(assertEffectTakesMillisBetween(0, 50)(effect3), Result.Error(e))

    val effect4 =
      Effect("hello").delayed(100).zipParEffect(Effect("world").delayed(100)) { (_, _) =>
        Effect.error(e)
      }

    assertEquals(assertEffectTakesMillisBetween(100, 150)(effect4), Result.Error(e))
  }

  test("`zip` runs both effects sequentially, when they are both successful, computes a value with results") {
    val effect1 =
      Effect("hello").delayed(100).zip(Effect("world").delayed(100)) { (h, w) =>
        s"$h $w"
      }

    assertEquals(assertEffectTakesMillisBetween(200, 250)(effect1), Result.Value("hello world"))

    val effect2 =
      Effect.error(e).zip(Effect("world").delayed(100)) { (h, w) =>
        s"$h $w"
      }

    assertEquals(assertEffectTakesMillisBetween(0, 50)(effect2), Result.Error(e))

    val effect3 =
      Effect("hello").delayed(100).zip(Effect.error(e)) { (h, w) =>
        s"$h $w"
      }

    assertEquals(assertEffectTakesMillisBetween(100, 150)(effect3), Result.Error(e))
  }

  test("`zipPar` runs both effects in parallel, when they are both successful, computes a value with results") {
    val effect1 =
      Effect("hello").delayed(100).zipPar(Effect("world").delayed(100)) { (h, w) =>
        s"$h $w"
      }

    assertEquals(assertEffectTakesMillisBetween(100, 150)(effect1), Result.Value("hello world"))

    val effect2 =
      Effect.error(e).zipPar(Effect("world").delayed(100)) { (h, w) =>
        s"$h $w"
      }

    assertEquals(assertEffectTakesMillisBetween(100, 150)(effect2), Result.Error(e))

    val effect3 =
      Effect("hello").delayed(100).zipPar(Effect.error(e)) { (h, w) =>
        s"$h $w"
      }

    assertEquals(assertEffectTakesMillisBetween(0, 50)(effect3), Result.Error(e))
  }

  test("`also` runs both effects sequentially and uses result of first effect, ignoring the result of second effect") {
    val effect1 = Effect("hello").delayed(100) also Effect("world").delayed(100)

    assertEquals(assertEffectTakesMillisBetween(200, 250)(effect1), Result.Value("hello"))

    val effect2 = Effect.error(e) also Effect("world").delayed(100)

    assertEquals(assertEffectTakesMillisBetween(0, 50)(effect2), Result.Error(e))

    val effect3 = Effect("hello").delayed(100) also Effect.error(e)

    assertEquals(assertEffectTakesMillisBetween(100, 150)(effect3), Result.Value("hello"))
  }

  test("`alsoPar` runs both effects in parallel and uses result of first effect, ignoring the result of second effect") {
    val effect1 = Effect("hello").delayed(100) alsoPar Effect("world").delayed(100)

    assertEquals(assertEffectTakesMillisBetween(100, 150)(effect1), Result.Value("hello"))

    val effect2 = Effect.error(e) alsoPar Effect("world").delayed(100)

    assertEquals(assertEffectTakesMillisBetween(0, 50)(effect2), Result.Error(e))

    val effect3 = Effect("hello").delayed(100) alsoPar Effect.error(e)

    assertEquals(assertEffectTakesMillisBetween(100, 150)(effect3), Result.Value("hello"))
  }

  test("`and` runs both effects sequentially, when they are both successful, uses the result of second effect") {
    val effect1 = Effect("hello").delayed(100) and Effect("world").delayed(100)

    assertEquals(assertEffectTakesMillisBetween(200, 250)(effect1), Result.Value("world"))

    val effect2 = Effect.error(e) and Effect("world").delayed(100)

    assertEquals(assertEffectTakesMillisBetween(0, 50)(effect2), Result.Error(e))

    val effect3 = Effect("hello").delayed(100) and Effect.error(e)

    assertEquals(assertEffectTakesMillisBetween(100, 150)(effect3), Result.Error(e))
  }

  test("`andPar` runs both effects in parallel, when they are both successful, uses the result of second effect") {
    val effect1 = Effect("hello").delayed(100) andPar Effect("world").delayed(100)

    assertEquals(assertEffectTakesMillisBetween(100, 150)(effect1), Result.Value("world"))

    val effect2 = Effect.error(e) andPar Effect("world").delayed(100)

    assertEquals(assertEffectTakesMillisBetween(100, 150)(effect2), Result.Error(e))

    val effect3 = Effect("hello").delayed(100) andPar Effect.error(e)

    assertEquals(assertEffectTakesMillisBetween(0, 50)(effect3), Result.Error(e))
  }

  test("`tuple` runs both effects sequentially, when they are both successful, computes a value with results") {
    val effect1 = Effect("hello").delayed(100) tuple Effect("world").delayed(100)

    assertEquals(assertEffectTakesMillisBetween(200, 250)(effect1), Result.Value("hello" -> "world"))

    val effect2 = Effect.error(e) tuple Effect("world").delayed(100)

    assertEquals(assertEffectTakesMillisBetween(0, 50)(effect2), Result.Error(e))

    val effect3 = Effect("hello").delayed(100) tuple Effect.error(e)

    assertEquals(assertEffectTakesMillisBetween(100, 150)(effect3), Result.Error(e))
  }

  test("`tuplePar` runs both effects in parallel, when they are both successful, computes a value with results") {
    val effect1 = Effect("hello").delayed(100) tuplePar Effect("world").delayed(100)

    assertEquals(assertEffectTakesMillisBetween(100, 150)(effect1), Result.Value("hello" -> "world"))

    val effect2 = Effect.error(e) tuplePar Effect("world").delayed(100)

    assertEquals(assertEffectTakesMillisBetween(100, 150)(effect2), Result.Error(e))

    val effect3 = Effect("hello").delayed(100) tuplePar Effect.error(e)

    assertEquals(assertEffectTakesMillisBetween(0, 50)(effect3), Result.Error(e))
  }

  test("`foldEffect` handles all cases of an effect to run given effect(s)") {
    Effect("hello")
      .foldEffect(
        {
          case Left(throwable) => Effect(s"throwable: $throwable")
          case Right(error)    => Effect(s"error: $error")
        },
        value => Effect(s"value: $value")
      )
      .assertValue("value: hello")

    Effect("hello")
      .foldEffect(
        {
          case Left(throwable) => Effect(s"throwable: $throwable")
          case Right(error)    => Effect(s"error: $error")
        },
        value => Effect.error(e)
      )
      .assertError(e)

    Effect("hello")
      .foldEffect(
        {
          case Left(throwable) => Effect(s"throwable: $throwable")
          case Right(error)    => Effect(s"error: $error")
        },
        value => Effect.unexpectedError(exception)
      )
      .assertUnexpectedError(exception)

    Effect
      .error(e)
      .foldEffect(
        {
          case Left(throwable) => Effect(s"throwable: $throwable")
          case Right(error)    => Effect(s"error: $error")
        },
        value => Effect(s"value: $value")
      )
      .assertValue(s"error: $e")

    Effect
      .error(e)
      .foldEffect(
        {
          case Left(throwable) => Effect(s"throwable: $throwable")
          case Right(error)    => Effect.error(e.withCode(1))
        },
        value => Effect(s"value: $value")
      )
      .assertError(e.withCode(1))

    Effect
      .error(e)
      .foldEffect(
        {
          case Left(throwable) => Effect(s"throwable: $throwable")
          case Right(error)    => Effect.unexpectedError(exception)
        },
        value => Effect(s"value: $value")
      )
      .assertUnexpectedError(exception)

    Effect
      .unexpectedError(exception)
      .foldEffect(
        {
          case Left(throwable) => Effect(s"throwable: $throwable")
          case Right(error)    => Effect(s"error: $error")
        },
        value => Effect(s"value: $value")
      )
      .assertValue(s"throwable: $exception")

    Effect
      .unexpectedError(exception)
      .foldEffect(
        {
          case Left(throwable) => Effect.error(e)
          case Right(error)    => Effect(s"error: $error")
        },
        value => Effect(s"value: $value")
      )
      .assertError(e)

    Effect
      .unexpectedError(exception)
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
    Effect("hello")
      .fold(
        {
          case Left(throwable) => s"throwable: $throwable"
          case Right(error)    => s"error: $error"
        },
        value => s"value: $value"
      )
      .assertValue("value: hello")

    Effect
      .error(e)
      .fold(
        {
          case Left(throwable) => s"throwable: $throwable"
          case Right(error)    => s"error: $error"
        },
        value => s"value: $value"
      )
      .assertValue(s"error: $e")

    Effect
      .unexpectedError(exception)
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
    Effect("hello")
      .handleAllErrorsEffect {
        case Left(throwable) => Effect(s"throwable: $throwable")
        case Right(error)    => Effect(s"error: $error")
      }
      .assertValue("hello")

    Effect
      .error(e)
      .handleAllErrorsEffect {
        case Left(throwable) => Effect(s"throwable: $throwable")
        case Right(error)    => Effect(s"error: $error")
      }
      .assertValue(s"error: $e")

    Effect
      .error(e)
      .handleAllErrorsEffect {
        case Left(throwable) => Effect(s"throwable: $throwable")
        case Right(error)    => Effect.error(e.withCode(1))
      }
      .assertError(e.withCode(1))

    Effect
      .error(e)
      .handleAllErrorsEffect {
        case Left(throwable) => Effect(s"throwable: $throwable")
        case Right(error)    => Effect.unexpectedError(exception)
      }
      .assertUnexpectedError(exception)

    Effect
      .unexpectedError(exception)
      .handleAllErrorsEffect {
        case Left(throwable) => Effect(s"throwable: $throwable")
        case Right(error)    => Effect(s"error: $error")
      }
      .assertValue(s"throwable: $exception")

    Effect
      .unexpectedError(exception)
      .handleAllErrorsEffect {
        case Left(throwable) => Effect.error(e)
        case Right(error)    => Effect(s"error: $error")
      }
      .assertError(e)

    Effect
      .unexpectedError(exception)
      .handleAllErrorsEffect {
        case Left(throwable) => Effect.unexpectedError(exception2)
        case Right(error)    => Effect(s"error: $error")
      }
      .assertUnexpectedError(exception2)
  }

  test("`handleAllErrors` handles error and unexpected error cases of an effect to compute a value") {
    Effect("hello")
      .handleAllErrors {
        case Left(throwable) => s"throwable: $throwable"
        case Right(error)    => s"error: $error"
      }
      .assertValue("hello")

    Effect
      .error(e)
      .handleAllErrors {
        case Left(throwable) => s"throwable: $throwable"
        case Right(error)    => s"error: $error"
      }
      .assertValue(s"error: $e")

    Effect
      .unexpectedError(exception)
      .handleAllErrors {
        case Left(throwable) => s"throwable: $throwable"
        case Right(error)    => s"error: $error"
      }
      .assertValue(s"throwable: $exception")
  }

  test("`handleUnexpectedErrorEffect` handles unexpected error case of an effect to run given effect") {
    Effect("hello")
      .handleUnexpectedErrorEffect { throwable => Effect(s"throwable: $throwable") }
      .assertValue("hello")

    Effect
      .error(e)
      .handleUnexpectedErrorEffect { throwable => Effect(s"throwable: $throwable") }
      .assertError(e)

    Effect
      .unexpectedError(exception)
      .handleUnexpectedErrorEffect { throwable => Effect(s"throwable: $throwable") }
      .assertValue(s"throwable: $exception")

    Effect
      .unexpectedError(exception)
      .handleUnexpectedErrorEffect { throwable => Effect.error(e) }
      .assertError(e)

    Effect
      .unexpectedError(exception)
      .handleUnexpectedErrorEffect { throwable => Effect.unexpectedError(exception2) }
      .assertUnexpectedError(exception2)
  }

  test("`handleUnexpectedError` handles unexpected error case of an effect to compute a value") {
    Effect("hello")
      .handleUnexpectedError { throwable => s"throwable: $throwable" }
      .assertValue("hello")

    Effect
      .error(e)
      .handleUnexpectedError { throwable => s"throwable: $throwable" }
      .assertError(e)

    Effect
      .unexpectedError(exception)
      .handleUnexpectedError { throwable => s"throwable: $throwable" }
      .assertValue(s"throwable: $exception")
  }

  test("`handleErrorEffect` handles error case of an effect to run given effect") {
    Effect("hello")
      .handleErrorEffect { e => Effect(s"error: $e") }
      .assertValue("hello")

    Effect
      .error(e)
      .handleErrorEffect { e => Effect(s"error: $e") }
      .assertValue(s"error: $e")

    Effect
      .error(e)
      .handleErrorEffect { e => Effect.error(e.withCode(1)) }
      .assertError(e.withCode(1))

    Effect
      .error(e)
      .handleErrorEffect { e => Effect.unexpectedError(exception2) }
      .assertUnexpectedError(exception2)

    Effect
      .unexpectedError(exception)
      .handleErrorEffect { e => Effect(s"error: $e") }
      .assertUnexpectedError(exception)
  }

  test("`handleError` handles error case of an effect to compute a value") {
    Effect("hello")
      .handleError { e => s"error: $e" }
      .assertValue("hello")

    Effect
      .error(e)
      .handleError { e => s"error: $e" }
      .assertValue(s"error: $e")

    Effect
      .unexpectedError(exception)
      .handleError { e => s"error: $e" }
      .assertUnexpectedError(exception)
  }

  test("`mapError` handles error case of an effect to modify the error") {
    Effect("hello")
      .mapError(_.withCode(1))
      .assertValue("hello")

    Effect
      .error(e)
      .mapError(_.withCode(1))
      .assertError(e.withCode(1))

    Effect
      .unexpectedError(exception)
      .mapError(_.withCode(1))
      .assertUnexpectedError(exception)
  }

  test("`mapUnexpectedError` handles unexpected error case of an effect to modify the unexpected error") {
    Effect("hello")
      .mapUnexpectedError(_.withSuppressed(exception2))
      .assertValue("hello")

    Effect
      .error(e)
      .mapUnexpectedError(_.withSuppressed(exception2))
      .assertError(e)

    Effect
      .unexpectedError(exception)
      .mapUnexpectedError(_.withSuppressed(exception2))
      .assertUnexpectedError(exception.withSuppressed(exception2))
  }

  test("`ensuring` makes sure given effect is run when the effect is completed in any way") {
    val counter1 = new AtomicInteger(0)
    Effect("hello").ensuring(Effect(counter1.incrementAndGet())).assertValue("hello")
    assertEquals(counter1.get(), 1)

    val counter2 = new AtomicInteger(0)
    Effect.error(e).ensuring(Effect(counter2.incrementAndGet())).assertError(e)
    assertEquals(counter2.get(), 1)

    val counter3 = new AtomicInteger(0)
    Effect.unexpectedError(exception).ensuring(Effect(counter3.incrementAndGet())).assertUnexpectedError(exception)
    assertEquals(counter3.get(), 1)

    /* TODO: `ensuring` doesn't work properly with interruptions
    val counter4 = new AtomicInteger(0)
    val effect = for {
      fiber <- Effect.unit.delayed(1000).ensuring(Effect(counter4.incrementAndGet())).fork
      _     <- Effect.unit.delayed(100)
      _     <- fiber.interrupt
    } yield ()
    assertEffectTakesMillisBetween(100, 150)(effect)
    assertEquals(counter4.get(), 1)
     */
  }

  /* TODO: Test is flaky
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
   */

  test("`repeat` repeats the effect given number of times as long as it is successful, ignoring previous result") {
    val counter1 = new AtomicInteger(0)

    val result1 = assertEffectTakesMillisBetween(300, 350) {
      Effect(counter1.incrementAndGet())
        .delayed(100)
        .repeat(3)
    }

    assertEquals(result1, Result.Value(()))
    assertEquals(counter1.get(), 3)

    val counter2 = new AtomicInteger(0)

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
    val counter1 = new AtomicInteger(0)

    assertEffectTakesMillisBetween(100, 150) {
      for {
        fiber <- Effect(counter1.incrementAndGet()).delayed(10).forever.fork
        _     <- Effect.unit.delayed(100)
        _     <- fiber.interrupt
      } yield ()
    }

    assert(counter1.get() <= 10, s"Counter value was: ${counter1.get()}")

    val counter2 = new AtomicInteger(0)

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

  /* TODO: Fix this
  test("`uninterruptible` makes an effect uninterruptible so even if it gets interrupt call, it finishes its work first") {
    val counter = new AtomicInteger(0)

    assertEffectTakesMillisBetween(100, 150) {
      for {
        fiber <- Effect(counter.incrementAndGet()).delayed(10).repeat(10).uninterruptible.fork
        _     <- Effect.unit.delayed(50)
        _     <- fiber.interrupt
      } yield ()
    }

    assertEquals(counter.get(), 10)
  }
   */
}
