package effect

trait EffectApp {
  import EffectApp.*

  def mainEffect(args: Array[String]): Effect[Any]

  def main(args: Array[String]): Unit = {
    // TODO: Need to interrupt on shutdown hook
    val result   = mainEffect(args).unsafeRun()
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

object EffectApp {
  val SuccessExitCode         = 0
  val ErrorExitCode           = 1
  val UnexpectedErrorExitCode = 2

  def getExitCode(result: Result[Any]): Int =
    result match {
      case Result.UnexpectedError(throwable) =>
        Console.err.println("Unexpected error!")
        throwable.printStackTrace(Console.err)
        UnexpectedErrorExitCode

      case Result.Error(e) =>
        Console.err.println(s"Failed: $e")
        ErrorExitCode

      case Result.Value(_) =>
        SuccessExitCode
    }
}
