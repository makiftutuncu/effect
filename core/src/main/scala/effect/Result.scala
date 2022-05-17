package effect

import scala.util.Try

enum Result[+A] {
  case Value(value: A)
  case Error(error: Either[Throwable, E])
}

object Result {
  def value[A](a: A): Result[A] = Value(a)

  def error[A](error: Either[Throwable, E]): Result[A] = Error(error)
}
