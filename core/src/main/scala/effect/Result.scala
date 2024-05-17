package effect

import e.scala.E

/** Result of a computation */
enum Result[+A] {

  /** A successful computation producing a value of type A */
  case Value(value: A)

  /** A failed computation producing an [[e.scala.E]] */
  case Error(error: E)

  /** An interrupted computation that has no produced value */
  case Interrupted

  /** An unexpectedly failed computation producing a [[java.lang.Throwable]] */
  case UnexpectedError(error: Throwable)
}
