package effect

class EffectAlsoTest extends TestSuite {
  test("runs both effects sequentially and uses result of first effect, ignoring the result of second effect") {
    val effect1 = helloEffect.delayed(100) also worldEffect.delayed(100)

    assertEffectTakesAboutMillis(200)(effect1).assertValue("hello")

    val effect2 = errorEffect also worldEffect.delayed(100)

    assertEffectTakesAboutMillis(0)(effect2).assertError(e)

    val effect3 = helloEffect.delayed(100) also errorEffect

    assertEffectTakesAboutMillis(100)(effect3).assertValue("hello")
  }
}
