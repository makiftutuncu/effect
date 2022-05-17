package effect

trait EffectApp {
  def effect(args: Array[String]): Effect[Any]

  protected def getExitCode(result: Result[Any]): Int =
    result match {
      case Result.Error(Left(throwable)) =>
        Console.err.println("Unhandled error!")
        throwable.printStackTrace(Console.err)
        2

      case Result.Error(Right(e)) =>
        Console.err.println(s"Failed: $e")
        1

      case Result.Value(_) =>
        0
    }

  def main(args: Array[String]): Unit = {
    // TODO: Need to interrupt on shutdown hook
    val result   = effect(args).unsafeRun()
    val exitCode = getExitCode(result)

    try {
      System.exit(exitCode)
    } catch {
      case e: SecurityException =>
        Console.err.println("Cannot exit!")
        e.printStackTrace(Console.err)
    }
  }
}
