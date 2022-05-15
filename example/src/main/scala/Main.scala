import effect.*

object Main {
  val program1: Effect[Int] =
    Effect.value(42)

  val program2: Effect[String] =
    for {
      int      <- Effect.value(42)
      string   <- Effect.value("hello")
      combined <- Effect.suspend(string + int)
      asyncResult <- Effect.callback { complete =>
        println("Async started")
        Thread.sleep(100)
        println(s"Done: $combined")
        complete(1)
      }
      result <- Effect.value(s"Result is $asyncResult")
    } yield {
      result
    }

  val program3: Effect[(Int, String)] =
    Effect.value(3) zip Effect.callback[String] { complete =>
      Thread.sleep(300)
      complete("foo")
    }

  val program4: Effect[Int] =
    Effect.fail(E(1, "error"))

  val program5: Effect[Int] =
    Effect.suspend {
      throw new Exception("boom")
    }

  def main(args: Array[String]): Unit =
    (program3 zip program5).unsafeRun {
      case Result.Error(Left(throwable)) =>
        println("Unhandled error!")
        throwable.printStackTrace()

      case Result.Error(Right(e)) =>
        println(s"Failed: $e")

      case Result.Value(value) =>
        println(value)
    }
}
