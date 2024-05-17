package effect

class EffectMapErrorTest extends TestSuite {
  test("doesn't run given conversion for an effect that completes successfully") {
    helloEffect.mapError(_.code(1)).assertValue("hello")
  }

  test("handles error of an effect that completes with error to run given conversion") {
    errorEffect.mapError(_.code(1)).assertError(e.code(1))
  }

  test("doesn't run given conversion for an effect that completes with unexpected error") {
    unexpectedErrorEffect.mapError(_.code(1)).assertUnexpectedError(exception)
  }

  test("doesn't run given conversion for an effect that is interrupted") {
    interruptedEffect.mapError(_.code(1)).assertInterrupted
  }
}
