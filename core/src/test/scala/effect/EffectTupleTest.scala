package effect

class EffectTupleTest extends TestSuite {
  test("runs both effects sequentially, when they are both successful, computes a value with results") {
    val effect1 = helloEffect.delayed(100) tuple worldEffect.delayed(100)

    assertEffectTakesAboutMillis(200)(effect1).assertValue("hello" -> "world")

    val effect2 = errorEffect tuple worldEffect.delayed(100)

    assertEffectTakesAboutMillis(0)(effect2).assertError(e)

    val effect3 = helloEffect.delayed(100) tuple errorEffect

    assertEffectTakesAboutMillis(100)(effect3).assertError(e)
  }
}
