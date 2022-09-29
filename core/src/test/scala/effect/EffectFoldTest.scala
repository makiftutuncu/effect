package effect

class EffectFoldTest extends TestSuite {
  test("handles all possible outcomes of an effect to run given conversion") {
    helloEffect.fold(handler).assertValue("value: hello")
    errorEffect.fold(handler).assertValue(s"error: $e")
    unexpectedErrorEffect.fold(handler).assertValue(s"unexpected error: $exception")
    interruptedEffect.fold(handler).assertValue("interrupted")
  }
}
