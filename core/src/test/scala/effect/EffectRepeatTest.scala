package effect

class EffectRepeatTest extends TestSuite {
  test("repeats the effect given number of times as long as it is successful, ignoring previous result") {
    val (counter1, effect1) = counterIncrementingEffect

    assertEffectTakesAboutMillis(300)(effect1.delayed(100).repeat(3)).assertValue(())
    assertEquals(counter1.get(), 3)

    val counter2 = getCounter

    assertEffectTakesAboutMillis(100) {
      Effect {
        val value = counter2.incrementAndGet()
        if (value > 1) {
          throw exception
        } else {
          Thread.sleep(100)
          value
        }
      }
        .repeat(3)
    }.assertUnexpectedError(exception)
    assertEquals(counter2.get(), 2)
  }
}
