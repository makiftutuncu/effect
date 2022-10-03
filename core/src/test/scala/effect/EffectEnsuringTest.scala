package effect

class EffectEnsuringTest extends TestSuite {
  test("runs given finalizer effect for an effect that completes successfully") {
    val (counter, finalizer) = counterIncrementingEffect
    assertEquals(counter.get(), 0)
    helloEffect.ensuring(finalizer).assertValue("hello")
    assertEquals(counter.get(), 1)
  }

  test("runs given finalizer effect for an effect that completes with error") {
    val (counter, finalizer) = counterIncrementingEffect
    assertEquals(counter.get(), 0)
    errorEffect.ensuring(finalizer).assertError(e)
    assertEquals(counter.get(), 1)
  }

  test("runs given finalizer effect for an effect that completes with unexpected error") {
    val (counter, finalizer) = counterIncrementingEffect
    assertEquals(counter.get(), 0)
    unexpectedErrorEffect.ensuring(finalizer).assertUnexpectedError(exception)
    assertEquals(counter.get(), 1)
  }

  test("runs given finalizer effect for an effect that is interrupted") {
    val (counter, finalizer) = counterIncrementingEffect
    assertEquals(counter.get(), 0)
    interruptedEffect.ensuring(finalizer).assertInterrupted
    assertEquals(counter.get(), 1)
  }
}
