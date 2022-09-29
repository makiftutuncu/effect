package effect

class EffectFlatMapTest extends TestSuite {
  test("doesn't run second effect when first one is not successful") {
    val (counter, secondEffect) = counterIncrementingEffect

    errorEffect.flatMap(_ => secondEffect).assertError(e)

    unexpectedErrorEffect.flatMap(_ => secondEffect).assertUnexpectedError(exception)

    interruptedEffect.flatMap(_ => secondEffect).assertInterrupted

    assertEquals(counter.get(), 0)
  }

  test("runs effects in sequence when first one is successful and completes with the result of the second effect") {
    helloEffect.flatMap(h => Effect(appendWorld(h))).assertValue("hello world")

    helloEffect.flatMap(_ => errorEffect).assertError(e)

    helloEffect.flatMap(_ => unexpectedErrorEffect).assertUnexpectedError(exception)

    helloEffect.flatMap(_ => interruptedEffect).assertInterrupted
  }
}
