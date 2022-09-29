package effect

class EffectForkTest extends TestSuite {
  test("runs the effect in a separate fiber") {
    val effect = for {
      fiber1 <- Effect("hello").delayed(100).fork
      fiber2 <- Effect("world").delayed(100).fork
      hello  <- fiber1.join
      world  <- fiber2.join
    } yield s"$hello $world"

    assertEffectTakesAboutMillis(100)(effect).assertValue("hello world")
  }
}
