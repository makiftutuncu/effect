package effect

class EffectMapDiscardingTest extends TestSuite {
  test("doesn't replace the value when the effect is not successful") {
    errorEffect.mapDiscarding("foo").assertError(e)

    unexpectedErrorEffect.mapDiscarding("foo").assertUnexpectedError(exception)

    interruptedEffect.mapDiscarding("foo").assertInterrupted
  }

  test("replaces the value when the effect is successful and completes with the replaced value, discarding the original success value") {
    helloEffect.mapDiscarding("foo").assertValue("foo")
  }
}
