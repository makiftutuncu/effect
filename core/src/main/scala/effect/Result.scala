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

  def from[A](t: Try[A]): Result[A] = t.fold(error, value)

  def from[A](either: Either[Throwable, E]): Result[A] = either.fold(error, error)

  extension [A](a: A)
    def toResult: Result[A] = value(a)

  extension [A](e: E)
    def toResult: Result[A] = error(e)

  extension [A](throwable: Throwable)
    def toResult: Result[A] = error(throwable)

  extension [A](t: Try[A])
    def toResult: Result[A] = from(t)

  extension [A](either: Either[Throwable, E])
    def toResult: Result[A] = from(either)
}
