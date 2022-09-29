package effect

class EffectCombineParTest extends TestSuite {
  test("runs both effects in parallel, when they are both successful, computes a value with results") {
    val effect1 =
      helloEffect.delayed(100).combinePar(worldEffect.delayed(100)) { (h, w) =>
        s"$h $w"
      }

    assertEffectTakesAboutMillis(100)(effect1).assertValue("hello world")

    val effect2 =
      errorEffect.combinePar(worldEffect.delayed(100)) { (h, w) =>
        s"$h $w"
      }

    assertEffectTakesAboutMillis(100)(effect2).assertError(e)

    val effect3 =
      helloEffect.delayed(100).combinePar(errorEffect) { (h, w) =>
        s"$h $w"
      }

    assertEffectTakesAboutMillis(0)(effect3).assertError(e)
  }
}
