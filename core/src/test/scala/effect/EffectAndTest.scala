package effect

class EffectAndTest extends TestSuite {
  test("runs both effects sequentially, when they are both successful, uses the result of second effect") {
    val effect1 = helloEffect.delayed(100) and worldEffect.delayed(100)

    assertEffectTakesAboutMillis(200)(effect1).assertValue("world")

    val effect2 = errorEffect and worldEffect.delayed(100)

    assertEffectTakesAboutMillis(0)(effect2).assertError(e)

    val effect3 = helloEffect.delayed(100) and errorEffect

    assertEffectTakesAboutMillis(100)(effect3).assertError(e)
  }
}
