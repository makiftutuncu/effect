package effect

class EffectUnitTest extends TestSuite {
  test("doesn't replace the value when the effect is not successful") {
    errorEffect.unit.assertError(e)

    unexpectedErrorEffect.unit.assertUnexpectedError(exception)

    interruptedEffect.unit.assertInterrupted
  }

  test("replaces the value when the effect is successful and completes with the replaced value, discarding the original success value") {
    helloEffect.unit.assertValue(())
  }
}
