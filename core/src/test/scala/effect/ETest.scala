package effect

import munit.FunSuite

import java.time.Instant

class ETest extends FunSuite {
  val e: E = E("test")

  test("checking if E has code") {
    assertEquals(e.hasCode, false)
    assertEquals(e.withCode(1).hasCode, true)
  }

  test("checking if E has kind") {
    assertEquals(e.hasKind, false)
    assertEquals(e.withKind("internal-error").hasKind, true)
  }

  test("checking if E has timestamp") {
    assertEquals(e.hasTimestamp, false)
    assertEquals(e.withTimestamp.hasTimestamp, true)
  }

  test("checking if E has causes") {
    assertEquals(e.hasCauses, false)
    assertEquals(e.addCauses(E("cause")).hasCauses, true)
  }

  test("checking if E has data") {
    assertEquals(e.hasData, false)
    assertEquals(e.addData("foo" -> "bar").hasData, true)
  }

  test("getting an E with code") {
    val before = e
    assertEquals(before.code, None)

    val after = before.withCode(1)
    assertEquals(after.code, Some(1))
  }

  test("getting an E with kind") {
    val before = e
    assertEquals(before.kind, None)

    val after = before.withKind("internal-error")
    assertEquals(after.kind, Some("internal-error"))
  }

  test("getting an E with timestamp") {
    val before = e
    assertEquals(before.timestamp, None)

    val now = Instant.now

    val after1 = before.withTimestamp
    assertEquals(after1.timestamp.exists(t => now.isBefore(t) || now == t), true)

    val after2 = before.withTimestamp(now)
    assertEquals(after2.timestamp, Some(now))
  }

  test("getting an E with causes") {
    val before = e
    assertEquals(before.causes, List.empty)

    val after1 = before.addCauses(E("cause1"), E("cause2"))
    assertEquals(after1.causes, List(E("cause1"), E("cause2")))

    val after2 = after1.withCauses(List(E("cause3")))
    assertEquals(after2.causes, List(E("cause3")))
  }

  test("getting an E with data") {
    val before = e
    assertEquals(before.data, Map.empty)

    val after1 = before.addData("foo" -> "bar", "test" -> "test")
    assertEquals(after1.data, Map("foo" -> "bar", "test" -> "test"))

    val after2 = after1.withData(Map("name" -> "John Doe"))
    assertEquals(after2.data, Map("name" -> "John Doe"))
  }

  test("converting E to an Exception") {
    val e = E("message")

    assertEquals(e.toException, E.AsException(e))
  }

  test("converting E to a String") {
    val expected =
      """{"message":"Failed: \"test\"","code":1,"kind":"internal","timestamp":"1970-01-01T00:00:00Z","causes":[{"message":"cause"}],"data":{"foo":"bar"}}"""

    val e = E(
      message = """Failed: "test"""",
      code = Some(1),
      kind = Some("internal"),
      timestamp = Some(Instant.EPOCH),
      causes = List(E("cause")),
      data = Map("foo" -> "bar")
    )

    assertEquals(e.toString, expected)
  }

  test("building E from Throwable") {
    val exception = new Exception("Cannot create user!")
      .initCause(
        new Exception("Form has errors!")
          .withSuppressed(new Exception("Name cannot be empty!"))
          .withSuppressed(new Exception("Age cannot be less than 18!"))
      )
      .withSuppressed(new Exception("suppressed"))

    val expected =
      E("Cannot create user!").addCauses(
        E("Form has errors!").addCauses(E("Name cannot be empty!"), E("Age cannot be less than 18!")),
        E("suppressed")
      )

    val actual = E.fromThrowable(exception)

    assertEquals(actual, expected)
  }

  extension (t: Throwable)
    def withSuppressed(other: Throwable): Throwable = {
      t.addSuppressed(other)
      t
    }
}
