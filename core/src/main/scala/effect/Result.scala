package effect

import scala.util.Try

enum Result[+A] {
  case Value(value: A)
  case Error(error: Either[Throwable, E])
}

object Result {
  def value[A](a: A): Result[A] = Value(a)

  def error[A](e: E): Result[A] = Error(Right(e))

  def error[A](throwable: Throwable): Result[A] = Error(Left(throwable))

  def from[A](either: Either[Throwable, E]): Result[A] = either.fold(throwable => Error(Left(throwable)), e => Error(Right(e)))
}
