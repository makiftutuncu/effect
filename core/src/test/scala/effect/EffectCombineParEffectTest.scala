package effect

class EffectCombineParEffectTest extends TestSuite {
  test("runs both effects in parallel, when they are both successful, runs given effect with results") {
    val effect1 =
      helloEffect.delayed(100).combineParEffect(worldEffect.delayed(100)) { (h, w) =>
        Effect(s"$h $w")
      }

    assertEffectTakesAboutMillis(100)(effect1).assertValue("hello world")

    val effect2 =
      errorEffect.combineParEffect(worldEffect.delayed(100)) { (h, w) =>
        Effect(s"$h $w")
      }

    assertEffectTakesAboutMillis(100)(effect2).assertError(e)

    val effect3 =
      helloEffect.delayed(100).combineParEffect(errorEffect) { (h, w) =>
        Effect(s"$h $w")
      }

    assertEffectTakesAboutMillis(0)(effect3).assertError(e)

    val effect4 =
      helloEffect.delayed(100).combineParEffect(worldEffect.delayed(100)) { (_, _) =>
        errorEffect
      }

    assertEffectTakesAboutMillis(100)(effect4).assertError(e)
  }
}
