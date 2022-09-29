package effect

class EffectHandleAllErrorsTest extends TestSuite {
  test("doesn't run given conversion for an effect that completes successfully") {
    helloEffect.handleAllErrors(allErrorsHandler).assertValue("hello")
  }

  test("handles error and unexpected error of an effect that completes with error to run given conversion") {
    errorEffect.handleAllErrors(allErrorsHandler).assertValue(s"error: $e")
  }

  test("handles error and unexpected error of an effect that completes with unexpected error to run given conversion") {
    unexpectedErrorEffect.handleAllErrors(allErrorsHandler).assertValue(s"unexpected error: $exception")
  }

  test("doesn't run given conversion for an effect that is interrupted") {
    interruptedEffect.handleAllErrors(allErrorsHandler).assertInterrupted
  }
}
