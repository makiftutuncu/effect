package effect

import scala.annotation.{tailrec, targetName}
import scala.collection.mutable
import scala.util.control.NonFatal

sealed trait Effect[+A] { self =>
  final def flatMap[B](f: A => Effect[B]): Effect[B] = Effect.FlatMap(self, f)

  final def map[B](f: A => B): Effect[B] = flatMap(a => Effect.Value(f(a)))

  final def zip[B](that: Effect[B]): Effect[(A, B)] =
    for {
      a <- self
      b <- that
    } yield (a, b)

  final def unsafeRun(callback: Result[A] => Unit): Unit = {
    type Erased       = Effect[Any]
    type Continuation = Any => Erased

    def erase[A](effect: Effect[A]): Erased                                 = effect
    def eraseContinuation[A, B](continuation: A => Effect[B]): Continuation = continuation.asInstanceOf[Continuation]
    def cast[A](any: Any): A                                                = any.asInstanceOf[A]

    var running: Boolean                           = true
    var instruction: Erased                        = erase(self)
    val continuations: mutable.Stack[Continuation] = mutable.Stack.empty

    def pause(): Unit = {
      running = false
      println(s"TRACE: pause loop")
    }

    def resume(): Unit = {
      running = true
      println(s"TRACE: resume loop")
      run()
    }

    def nextContinuation(value: Any): Unit =
      if (continuations.isEmpty) {
        running = false
        println(s"TRACE: no more continuations, complete with $value")
        callback(Result.value(cast(value)))
      } else {
        val continuation = continuations.pop()
        println(s"TRACE: next continuation with $value")
        instruction = continuation(value)
      }

    def fail(error: Either[Throwable, E]): Unit = {
      running = false
      println(s"TRACE: fail with $error")
      callback(Result.from(error))
    }

    def run(): Unit =
      try {
        while (running) {
          instruction match {
            case Effect.Value(value) =>
              nextContinuation(value)

            case Effect.Suspend(getValue) =>
              nextContinuation(getValue())

            case Effect.Error(error) =>
              fail(error)

            case Effect.FlatMap(effect, continuation) =>
              continuations.push(eraseContinuation(continuation))
              instruction = effect

            case Effect.Callback(register) =>
              pause()
              if (continuations.isEmpty) {
                register { value =>
                  println(s"TRACE: complete callback with $value")
                  callback(Result.value(cast(value)))
                }
              } else {
                register { value =>
                  println(s"TRACE: complete callback with $value")
                  instruction = Effect.Value(value)
                  resume()
                }
              }
          }
        }
      } catch {
        case NonFatal(throwable) =>
          fail(Left(throwable))
      }

    println(s"TRACE: start running")
    run()
  }
}

object Effect {
  def value[A](a: A): Effect[A] = Value(a)

  def suspend[A](a: => A): Effect[A] = Suspend(() => a)

  def callback[A](register: (A => Any) => Any): Effect[A] = Callback(register)

  def fail[A](e: E): Effect[A] = Error(Right(e))

  private[effect] final case class Value[+A](value: A) extends Effect[A]

  private[effect] final case class Suspend[+A](value: () => A) extends Effect[A]

  private[effect] final case class Error(error: Either[Throwable, E]) extends Effect[Nothing]

  private[effect] final case class FlatMap[A, +B](effect: Effect[A], continuation: A => Effect[B]) extends Effect[B]

  private[effect] final case class Callback[+A](register: (A => Any) => Any) extends Effect[A]
}
