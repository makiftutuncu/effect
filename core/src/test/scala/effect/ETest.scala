package effect

import munit.FunSuite

import java.time.Instant

class ETest extends FunSuite {
  test("checking if E has code") {
    assert(!E.empty.hasCode)
    assert(E.empty.withCode(1).hasCode)
  }

  test("checking if E has kind") {
    assert(!E.empty.hasKind)
    assert(E.empty.withKind("internal-error").hasKind)
  }

  test("checking if E has timestamp") {
    assert(!E.empty.hasTimestamp)
    assert(E.empty.withTimestamp.hasTimestamp)
  }

  test("checking if E has causes") {
    assert(!E.empty.hasCauses)
    assert(E.empty.addCauses(E("cause")).hasCauses)
  }

  test("checking if E has data") {
    assert(!E.empty.hasData)
    assert(E.empty.addData("foo" -> "bar").hasData)
  }

  test("setting code of an E") {
    val before = E.empty
    assertEquals(before.code, None)

    val after = before.withCode(1)
    assertEquals(after.code, Some(1))
  }

  test("setting kind of an E") {
    val before = E.empty
    assertEquals(before.kind, None)

    val after = before.withKind("internal-error")
    assertEquals(after.kind, Some("internal-error"))
  }

  test("setting timestamp of an E") {
    val before = E.empty
    assertEquals(before.timestamp, None)

    val now = Instant.now

    val after1 = before.withTimestamp
    assert(after1.timestamp.exists(t => now.isBefore(t) || now == t))

    val after2 = before.withTimestamp(now)
    assertEquals(after2.timestamp, Some(now))
  }

  test("setting causes of an E") {
    val before = E.empty
    assertEquals(before.causes, List.empty)

    val after1 = before.addCauses(E("cause1"), E("cause2"))
    assertEquals(after1.causes, List(E("cause1"), E("cause2")))

    val after2 = after1.withCauses(List(E("cause3")))
    assertEquals(after2.causes, List(E("cause3")))
  }

  test("setting data of an E") {
    val before = E.empty
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
    val exception = new Exception("Cannot create user!").initCause(
      new Exception("Form has errors!")
        .withSuppressed(new Exception("Name cannot be empty!"))
        .withSuppressed(new Exception("Age cannot be less than 18!"))
    )

    val expected =
      E("Cannot create user!").addCauses(E("Form has errors!").addCauses(E("Name cannot be empty!"), E("Age cannot be less than 18!")))

    val actual = E.fromThrowable(exception)

    assertEquals(actual, expected)
  }

  extension (e: Exception)
    def withSuppressed(other: Exception): Exception = {
      e.addSuppressed(other)
      e
    }
}
