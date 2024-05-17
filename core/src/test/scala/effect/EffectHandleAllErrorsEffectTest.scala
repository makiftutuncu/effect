package effect

class EffectHandleAllErrorsEffectTest extends TestSuite {
  test("doesn't run given effect for an effect that completes successfully") {
    helloEffect.handleAllErrorsEffect(allErrorsHandlerEffect).assertValue("hello")
    helloEffect.handleAllErrorsEffect(_ => errorEffect).assertValue("hello")
    helloEffect.handleAllErrorsEffect(_ => unexpectedErrorEffect).assertValue("hello")
    helloEffect.handleAllErrorsEffect(_ => interruptedEffect).assertValue("hello")
  }

  test("handles error and unexpected error of an effect that completes with error to run given effect") {
    errorEffect.handleAllErrorsEffect(allErrorsHandlerEffect).assertValue(s"error: $e")
    errorEffect.handleAllErrorsEffect(_ => Effect.error(e.code(1))).assertError(e.code(1))
    errorEffect.handleAllErrorsEffect(_ => unexpectedErrorEffect).assertUnexpectedError(exception)
    errorEffect.handleAllErrorsEffect(_ => interruptedEffect).assertInterrupted
  }

  test("handles error and unexpected error of an effect that completes with unexpected error to run given effect") {
    val exception2: Exception = Exception("test-2")

    unexpectedErrorEffect.handleAllErrorsEffect(allErrorsHandlerEffect).assertValue(s"unexpected error: $exception")
    unexpectedErrorEffect.handleAllErrorsEffect(_ => errorEffect).assertError(e)
    unexpectedErrorEffect.handleAllErrorsEffect(_ => Effect.unexpectedError(exception2)).assertUnexpectedError(exception2)
    unexpectedErrorEffect.handleAllErrorsEffect(_ => interruptedEffect).assertInterrupted
  }

  test("doesn't run given effect for an effect that is interrupted") {
    interruptedEffect.handleAllErrorsEffect(allErrorsHandlerEffect).assertInterrupted
    interruptedEffect.handleAllErrorsEffect(_ => errorEffect).assertInterrupted
    interruptedEffect.handleAllErrorsEffect(_ => unexpectedErrorEffect).assertInterrupted

    val counter = getCounter
    interruptedEffect.handleAllErrorsEffect { _ =>
      Effect.callback { complete =>
        counter.incrementAndGet()
        complete(Result.Interrupted)
      }
    }.assertInterrupted
    assertEquals(counter.get(), 0)
  }
}
