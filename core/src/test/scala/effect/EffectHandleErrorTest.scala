package effect

class EffectHandleErrorTest extends TestSuite {
  test("doesn't run given conversion for an effect that completes successfully") {
    helloEffect.handleError(errorHandler).assertValue("hello")
  }

  test("handles error of an effect that completes with error to run given conversion") {
    errorEffect.handleError(errorHandler).assertValue(s"error: $e")
  }

  test("doesn't run given conversion for an effect that completes with unexpected error") {
    unexpectedErrorEffect.handleError(errorHandler).assertUnexpectedError(exception)
  }

  test("doesn't run given conversion for an effect that is interrupted") {
    interruptedEffect.handleError(errorHandler).assertInterrupted
  }
}
