package effect

class EffectAlsoParTest extends TestSuite {
  test("runs both effects in parallel and uses result of first effect, ignoring the result of second effect") {
    val effect1 = helloEffect.delayed(100) alsoPar worldEffect.delayed(100)

    assertEffectTakesAboutMillis(100)(effect1).assertValue("hello")

    val effect2 = errorEffect alsoPar worldEffect.delayed(100)

    assertEffectTakesAboutMillis(0)(effect2).assertError(e)

    val effect3 = helloEffect.delayed(100) alsoPar errorEffect

    assertEffectTakesAboutMillis(100)(effect3).assertValue("hello")
  }
}
