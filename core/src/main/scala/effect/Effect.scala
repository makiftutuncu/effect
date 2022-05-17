package effect

import java.util.concurrent.CountDownLatch

import scala.annotation.{tailrec, targetName}
import scala.collection.mutable
import scala.util.control.NonFatal

sealed trait Effect[+A] { self =>
  protected def describe(): String

  override def toString: String = describe()

  // --- Core operators ---

  final def flatMap[B](f: A => Effect[B]): Effect[B] =
    Effect.FlatMap(self, f)

  final def fork: Effect[Fiber[A]] =
    Effect.Fork(self)

  final def fold[B](ifError: Either[Throwable, E] => Effect[B], ifValue: A => Effect[B]): Effect[B] =
    Effect.Fold(self, ifError, ifValue)

  // --- Derived operators ---

  final def map[B](f: A => B): Effect[B] =
    flatMap(a => Effect.Value(f(a)))

  final def zip[B](that: Effect[B]): Effect[(A, B)] =
    for {
      a <- self
      b <- that
    } yield {
      (a, b)
    }

  final def zipPar[B](that: Effect[B]): Effect[(A, B)] =
    for {
      aFiber <- self.fork
      b      <- that
      a      <- aFiber.join
    } yield {
      (a, b)
    }

  final def recoverUnexpectedError(handler: Throwable => E): Effect[A] =
    fold(
      {
        case Left(throwable) => Effect.Error(Right(handler(throwable)))
        case Right(e)        => Effect.Error(Right(e))
      },
      value => Effect.Value(value)
    )

  final def handleErrorEffect[AA >: A](handler: E => Effect[AA]): Effect[AA] =
    fold(
      {
        case Left(throwable) => throw throwable
        case Right(e)        => handler(e)
      },
      value => Effect.Value(value)
    )

  final def handleError[AA >: A](handler: E => AA): Effect[AA] =
    handleErrorEffect(e => Effect.Value(handler(e)))

  // -- Unsafe area ---

  final def unsafeRun(): Result[A] = {
    val latch  = new CountDownLatch(1)
    var result = null.asInstanceOf[Result[A]]
    val effect = self.fold(
      error =>
        Effect.Value {
          result = Result.error(error)
          latch.countDown()
        },
      value =>
        Effect.Value {
          result = Result.value(value)
          latch.countDown()
        }
    )
    Fiber(effect)
    latch.await()
    result
  }
}

object Effect {
  def value[A](a: A): Effect[A] = Value(a)

  def suspend[A](a: => A): Effect[A] = Suspend(() => a)

  def error[A](e: E): Effect[A] = Error(Right(e))

  def callback[A](register: (Result[A] => Any) => Any): Effect[A] = Callback(register)

  private[effect] final case class Value[+A](value: A) extends Effect[A] {
    override protected def describe(): String = s"Value($value)"
  }

  private[effect] final case class Suspend[+A](value: () => A) extends Effect[A] {
    override protected def describe(): String = s"Suspend($value)"
  }

  private[effect] final case class Error(error: Either[Throwable, E]) extends Effect[Nothing] {
    override protected def describe(): String = s"Error($error)"
  }

  private[effect] final case class Callback[+A](register: (Result[A] => Any) => Any) extends Effect[A] {
    override protected def describe(): String = s"Callback()"
  }

  private[effect] final case class FlatMap[A, +B](effect: Effect[A], continuation: A => Effect[B]) extends Effect[B] {
    override protected def describe(): String = s"FlatMap($effect)"
  }

  private[effect] final case class Fork[+A](effect: Effect[A]) extends Effect[Fiber[A]] {
    override protected def describe(): String = s"Fork($effect)"
  }

  private[effect] final case class Fold[A, +B](effect: Effect[A], ifError: Either[Throwable, E] => Effect[B], ifValue: A => Effect[B])
      extends Effect[B]
      with (A => Effect[B]) {
    override protected def describe(): String = "Fold()"

    override def apply(a: A): Effect[B] = ifValue(a)
  }
}
