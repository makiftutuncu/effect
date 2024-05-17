package effect

class EffectFoldEffectTest extends TestSuite {
  test("handles an effect that completes successfully to run given effect") {
    helloEffect.foldEffect(handlerEffect).assertValue("value: hello")

    helloEffect
      .foldEffect {
        case Result.Value(_) => errorEffect
        case result          => handlerEffect(result)
      }
      .assertError(e)

    helloEffect
      .foldEffect {
        case Result.Value(_) => unexpectedErrorEffect
        case result          => handlerEffect(result)
      }
      .assertUnexpectedError(exception)

    helloEffect.foldEffect {
      case Result.Value(_) => interruptedEffect
      case result          => handlerEffect(result)
    }.assertInterrupted
  }

  test("handles an effect that completes with error to run given effect") {
    errorEffect.foldEffect(handlerEffect).assertValue(s"error: $e")

    errorEffect
      .foldEffect {
        case Result.Error(e) => Effect.error(e.code(1))
        case result          => handlerEffect(result)
      }
      .assertError(e.code(1))

    errorEffect
      .foldEffect {
        case Result.Error(_) => unexpectedErrorEffect
        case result          => handlerEffect(result)
      }
      .assertUnexpectedError(exception)

    errorEffect.foldEffect {
      case Result.Error(_) => interruptedEffect
      case result          => handlerEffect(result)
    }.assertInterrupted
  }

  test("handles an effect that completes with unexpected error to run given effect") {
    val exception2: Exception = Exception("test-2")

    unexpectedErrorEffect.foldEffect(handlerEffect).assertValue(s"unexpected error: $exception")

    unexpectedErrorEffect
      .foldEffect {
        case Result.UnexpectedError(_) => errorEffect
        case result                    => handlerEffect(result)
      }
      .assertError(e)

    unexpectedErrorEffect
      .foldEffect {
        case Result.UnexpectedError(_) => Effect.unexpectedError(exception2)
        case result                    => handlerEffect(result)
      }
      .assertUnexpectedError(exception2)

    unexpectedErrorEffect.foldEffect {
      case Result.UnexpectedError(_) => interruptedEffect
      case result                    => handlerEffect(result)
    }.assertInterrupted
  }

  test("handles an effect that is interrupted to run given effect") {
    interruptedEffect.foldEffect(handlerEffect).assertValue("interrupted")

    interruptedEffect
      .foldEffect {
        case Result.Interrupted => errorEffect
        case result             => handlerEffect(result)
      }
      .assertError(e)

    interruptedEffect
      .foldEffect {
        case Result.Interrupted => unexpectedErrorEffect
        case result             => handlerEffect(result)
      }
      .assertUnexpectedError(exception)

    val counter = getCounter
    interruptedEffect.foldEffect {
      case Result.Interrupted =>
        Effect.callback { complete =>
          counter.incrementAndGet()
          complete(Result.Interrupted)
        }
      case result => handlerEffect(result)
    }.assertInterrupted
    assertEquals(counter.get(), 1)
  }
}
