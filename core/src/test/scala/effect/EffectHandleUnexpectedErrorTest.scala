package effect

class EffectHandleUnexpectedErrorTest extends TestSuite {
  test("doesn't run given conversion for an effect that completes successfully") {
    helloEffect.handleUnexpectedError(unexpectedErrorHandler).assertValue("hello")
  }

  test("doesn't run given conversion for an effect that completes with error") {
    errorEffect.handleUnexpectedError(unexpectedErrorHandler).assertError(e)
  }

  test("handles unexpected error of an effect that completes with unexpected error to run given conversion") {
    unexpectedErrorEffect.handleUnexpectedError(unexpectedErrorHandler).assertValue(s"unexpected error: $exception")
  }

  test("doesn't run given conversion for an effect that is interrupted") {
    interruptedEffect.handleUnexpectedError(unexpectedErrorHandler).assertInterrupted
  }
}
