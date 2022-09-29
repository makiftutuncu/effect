package effect

class EffectAndParTest extends TestSuite {
  test("runs both effects in parallel, when they are both successful, uses the result of second effect") {
    val effect1 = helloEffect.delayed(100) andPar worldEffect.delayed(100)

    assertEffectTakesAboutMillis(100)(effect1).assertValue("world")

    val effect2 = errorEffect andPar worldEffect.delayed(100)

    assertEffectTakesAboutMillis(100)(effect2).assertError(e)

    val effect3 = helloEffect.delayed(100) andPar errorEffect

    assertEffectTakesAboutMillis(0)(effect3).assertError(e)
  }
}
