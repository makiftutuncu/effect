package effect

class EffectHandleErrorEffectTest extends TestSuite {
  test("doesn't run given effect for an effect that completes successfully") {
    helloEffect.handleErrorEffect(errorHandlerEffect).assertValue("hello")
    helloEffect.handleErrorEffect(_ => errorEffect).assertValue("hello")
    helloEffect.handleErrorEffect(_ => unexpectedErrorEffect).assertValue("hello")
    helloEffect.handleErrorEffect(_ => interruptedEffect).assertValue("hello")
  }

  test("handles error of an effect that completes with error to run given effect") {
    errorEffect.handleErrorEffect(errorHandlerEffect).assertValue(s"error: $e")
    errorEffect.handleErrorEffect(_ => Effect.error(e.withCode(1))).assertError(e.withCode(1))
    errorEffect.handleErrorEffect(_ => unexpectedErrorEffect).assertUnexpectedError(exception)
    errorEffect.handleErrorEffect(_ => interruptedEffect).assertInterrupted
  }

  test("doesn't run given effect for an effect that completes with unexpected error") {
    val exception2: Exception = Exception("test-2")

    unexpectedErrorEffect.handleErrorEffect(errorHandlerEffect).assertUnexpectedError(exception)
    unexpectedErrorEffect.handleErrorEffect(_ => errorEffect).assertUnexpectedError(exception)
    unexpectedErrorEffect.handleErrorEffect(_ => Effect.unexpectedError(exception2)).assertUnexpectedError(exception)
    unexpectedErrorEffect.handleErrorEffect(_ => interruptedEffect).assertUnexpectedError(exception)
  }

  test("doesn't run given effect for an effect that is interrupted") {
    interruptedEffect.handleErrorEffect(errorHandlerEffect).assertInterrupted
    interruptedEffect.handleErrorEffect(_ => errorEffect).assertInterrupted
    interruptedEffect.handleErrorEffect(_ => unexpectedErrorEffect).assertInterrupted

    val counter = getCounter
    interruptedEffect.handleErrorEffect { _ =>
      Effect.callback { complete =>
        counter.incrementAndGet()
        complete(Result.Interrupted)
      }
    }.assertInterrupted
    assertEquals(counter.get(), 0)
  }
}
