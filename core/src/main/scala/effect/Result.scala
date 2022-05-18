package effect

import scala.util.Try

enum Result[+A] {
  case Value(value: A)
  case Error(error: Either[Throwable, E])
}
