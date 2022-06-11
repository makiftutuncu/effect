package effect

enum Result[+A] {
  case Value(value: A)
  case Error(error: E)
  case UnexpectedError(error: Throwable)
}
