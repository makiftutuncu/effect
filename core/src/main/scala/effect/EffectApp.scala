package effect

import scala.concurrent.ExecutionContextExecutor

/** Entry point of an effectful application, to provide a root effect and customize global application behavior */
trait EffectApp {
  import EffectApp.*

  /** Default execution context to use when it is not configured */
  val executor: ExecutionContextExecutor = Effect.defaultExecutor

  /** Whether to print debugging information as effects of this application run */
  val traceEnabled: Boolean = false

  /** Main effect of the effectful application
    *
    * @param args
    *   command line arguments given to the application
    */
  def mainEffect(args: Array[String]): Effect[Any]

  def main(args: Array[String]): Unit = {
    // TODO: Need to interrupt on shutdown hook
    val result   = mainEffect(args).unsafeRun(executor, traceEnabled)
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
  private val SuccessExitCode         = 0
  private val ErrorExitCode           = 1
  private val InterruptedExitCode     = 2
  private val UnexpectedErrorExitCode = 3

  /** Gets the exit code of application process based on result of the main effect
    *
    * @param result
    *   result of the main effect
    *
    * @return
    *   the exit code of application process based on result of the main effect
    */
  def getExitCode(result: Result[Any]): Int =
    result match {
      case Result.UnexpectedError(throwable) =>
        Console.err.println("Unexpected error!")
        throwable.printStackTrace(Console.err)
        UnexpectedErrorExitCode

      case Result.Interrupted =>
        Console.err.println("Interrupted!")
        InterruptedExitCode

      case Result.Error(e) =>
        Console.err.println(s"Failed: $e")
        ErrorExitCode

      case Result.Value(_) =>
        SuccessExitCode
    }
}
