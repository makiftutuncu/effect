package effect

class EffectCombineEffectTest extends TestSuite {
  test("runs both effects sequentially, when they are both successful, runs given effect with results") {
    val effect1 =
      helloEffect.delayed(100).combineEffect(worldEffect.delayed(100)) { (h, w) =>
        Effect(s"$h $w")
      }

    assertEffectTakesAboutMillis(200)(effect1).assertValue("hello world")

    val effect2 =
      errorEffect.combineEffect(worldEffect.delayed(100)) { (h, w) =>
        Effect(s"$h $w")
      }

    assertEffectTakesAboutMillis(0)(effect2).assertError(e)

    val effect3 =
      helloEffect.delayed(100).combineEffect(errorEffect) { (h, w) =>
        Effect(s"$h $w")
      }

    assertEffectTakesAboutMillis(100)(effect3).assertError(e)

    val effect4 =
      helloEffect.delayed(100).combineEffect(worldEffect.delayed(100)) { (_, _) =>
        errorEffect
      }

    assertEffectTakesAboutMillis(200)(effect4).assertError(e)
  }
}
