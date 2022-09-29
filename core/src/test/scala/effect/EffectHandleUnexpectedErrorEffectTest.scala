package effect

class EffectHandleUnexpectedErrorEffectTest extends TestSuite {
  test("doesn't run given effect for an effect that completes successfully") {
    helloEffect.handleUnexpectedErrorEffect(unexpectedErrorHandlerEffect).assertValue("hello")
    helloEffect.handleUnexpectedErrorEffect(_ => errorEffect).assertValue("hello")
    helloEffect.handleUnexpectedErrorEffect(_ => unexpectedErrorEffect).assertValue("hello")
    helloEffect.handleUnexpectedErrorEffect(_ => interruptedEffect).assertValue("hello")
  }

  test("doesn't run given effect for an effect that completes with error") {
    errorEffect.handleUnexpectedErrorEffect(unexpectedErrorHandlerEffect).assertError(e)
    errorEffect.handleUnexpectedErrorEffect(_ => Effect.error(e.withCode(1))).assertError(e)
    errorEffect.handleUnexpectedErrorEffect(_ => unexpectedErrorEffect).assertError(e)
    errorEffect.handleUnexpectedErrorEffect(_ => interruptedEffect).assertError(e)
  }

  test("handles unexpected error of an effect that completes with unexpected error to run given effect") {
    val exception2: Exception = Exception("test-2")

    unexpectedErrorEffect.handleUnexpectedErrorEffect(unexpectedErrorHandlerEffect).assertValue(s"unexpected error: $exception")
    unexpectedErrorEffect.handleUnexpectedErrorEffect(_ => errorEffect).assertError(e)
    unexpectedErrorEffect.handleUnexpectedErrorEffect(_ => Effect.unexpectedError(exception2)).assertUnexpectedError(exception2)
    unexpectedErrorEffect.handleUnexpectedErrorEffect(_ => interruptedEffect).assertInterrupted
  }

  test("doesn't run given effect for an effect that is interrupted") {
    interruptedEffect.handleUnexpectedErrorEffect(unexpectedErrorHandlerEffect).assertInterrupted
    interruptedEffect.handleUnexpectedErrorEffect(_ => errorEffect).assertInterrupted
    interruptedEffect.handleUnexpectedErrorEffect(_ => unexpectedErrorEffect).assertInterrupted

    val counter = getCounter
    interruptedEffect.handleUnexpectedErrorEffect { _ =>
      Effect.callback { complete =>
        counter.incrementAndGet()
        complete(Result.Interrupted)
      }
    }.assertInterrupted
    assertEquals(counter.get(), 0)
  }
}
