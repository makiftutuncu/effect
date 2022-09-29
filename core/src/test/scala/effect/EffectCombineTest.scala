package effect

class EffectCombineTest extends TestSuite {
  test("runs both effects sequentially, when they are both successful, computes a value with results") {
    val effect1 =
      helloEffect.delayed(100).combine(worldEffect.delayed(100)) { (h, w) =>
        s"$h $w"
      }

    assertEffectTakesAboutMillis(200)(effect1).assertValue("hello world")

    val effect2 =
      errorEffect.combine(worldEffect.delayed(100)) { (h, w) =>
        s"$h $w"
      }

    assertEffectTakesAboutMillis(0)(effect2).assertError(e)

    val effect3 =
      helloEffect.delayed(100).combine(errorEffect) { (h, w) =>
        s"$h $w"
      }

    assertEffectTakesAboutMillis(100)(effect3).assertError(e)
  }
}
