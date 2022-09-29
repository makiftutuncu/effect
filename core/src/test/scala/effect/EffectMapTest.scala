package effect

class EffectMapTest extends TestSuite {
  test("doesn't run the conversion when the effect is not successful") {
    errorEffect.map(appendWorld).assertError(e)

    unexpectedErrorEffect.map(appendWorld).assertUnexpectedError(exception)

    interruptedEffect.map(appendWorld).assertInterrupted
  }

  test("runs the conversion when the effect is successful and completes with the converted success value") {
    helloEffect.map(appendWorld).assertValue("hello world")
  }
}
