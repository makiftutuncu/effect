package effect

class EffectMapUnexpectedErrorTest extends TestSuite {
  val exception2: Exception = Exception("test2")

  test("doesn't run given conversion for an effect that completes successfully") {
    helloEffect.mapUnexpectedError(_ => exception2).assertValue("hello")
  }

  test("doesn't run given conversion for an effect that completes with error") {
    errorEffect.mapUnexpectedError(_ => exception2).assertError(e)
  }

  test("handles unexpected error of an effect that completes with unexpected error to run given conversion") {
    unexpectedErrorEffect.mapUnexpectedError(_ => exception2).assertUnexpectedError(exception2)
  }

  test("doesn't run given conversion for an effect that is interrupted") {
    interruptedEffect.mapUnexpectedError(_ => exception2).assertInterrupted
  }
}
