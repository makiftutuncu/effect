package effect

class EffectTupleParTest extends TestSuite {
  test("runs both effects in parallel, when they are both successful, computes a value with results") {
    val effect1 = helloEffect.delayed(100) tuplePar worldEffect.delayed(100)

    assertEffectTakesAboutMillis(100)(effect1).assertValue("hello" -> "world")

    val effect2 = errorEffect tuplePar worldEffect.delayed(100)

    assertEffectTakesAboutMillis(100)(effect2).assertError(e)

    val effect3 = helloEffect.delayed(100) tuplePar errorEffect

    assertEffectTakesAboutMillis(0)(effect3).assertError(e)
  }
}
