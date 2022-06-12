package effect

import java.util.concurrent.CountDownLatch

import scala.annotation.{tailrec, targetName}
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.util.control.NonFatal

sealed trait Effect[+A] { self =>
  final def flatMap[B](f: A => Effect[B]): Effect[B] =
    Effect.FlatMap(self, f)

  final def map[B](f: A => B): Effect[B] =
    flatMap(a => Effect.Value(f(a)))

  final def as[B](b: => B): Effect[B] =
    map(_ => b)

  final def fork: Effect[Fiber[A]] =
    Effect.Fork(self)

  final def zip[B, C](that: => Effect[B])(f: (A, B) => C): Effect[C] =
    for {
      a <- self
      b <- that
    } yield {
      f(a, b)
    }

  final def also[B](that: => Effect[B]): Effect[A] =
    zip(that)((a, _) => a)

  final def and[B](that: => Effect[B]): Effect[B] =
    zip(that)((_, b) => b)

  final def zipPar[B, C](that: => Effect[B])(f: (A, B) => C): Effect[C] =
    for {
      aFiber <- self.fork
      b      <- that
      a      <- aFiber.join
    } yield {
      f(a, b)
    }

  final def alsoPar[B](that: => Effect[B]): Effect[A] =
    zipPar(that)((a, _) => a)

  final def andPar[B](that: => Effect[B]): Effect[B] =
    zipPar(that)((_, b) => b)

  final def tuple[B](that: => Effect[B]): Effect[(A, B)] =
    zip(that)((a, b) => (a, b))

  final def tuplePar[B](that: => Effect[B]): Effect[(A, B)] =
    zipPar(that)((a, b) => (a, b))

  final def foldEffect[B](ifError: E => Effect[B], ifValue: A => Effect[B]): Effect[B] =
    Effect.Fold(
      self,
      {
        case Left(throwable) => Effect.Error(Left(throwable))
        case Right(e)        => ifError(e)
      },
      ifValue
    )

  final def fold[B](ifError: E => B, ifValue: A => B): Effect[B] =
    foldEffect(e => Effect.Value(ifError(e)), a => Effect.Value(ifValue(a)))

  final def recoverEffect[AA >: A](handler: Throwable => Effect[AA]): Effect[AA] =
    Effect.Fold(
      self,
      {
        case Left(throwable) => handler(throwable)
        case Right(e)        => Effect.Error(Right(e))
      },
      value => Effect.Value(value)
    )

  final def recover[AA >: A](handler: Throwable => AA): Effect[AA] =
    recoverEffect(throwable => Effect.Value(handler(throwable)))

  final def handleErrorEffect[AA >: A](handler: E => Effect[AA]): Effect[AA] =
    foldEffect(handler, Effect.Value.apply)

  final def handleError[AA >: A](handler: E => AA): Effect[AA] =
    handleErrorEffect(e => Effect.Value(handler(e)))

  final def refineError(handler: Throwable => E): Effect[A] =
    recoverEffect(throwable => Effect.Error(Right(handler(throwable))))

  final def finalize(finalizer: => Effect[Any]): Effect[A] =
    foldEffect(e => finalizer and Effect.Error(Right(e)), a => finalizer and Effect.Value(a)).recoverEffect(e =>
      finalizer and Effect.Error(Left(e))
    )

  final def on(executor: ExecutionContextExecutor): Effect[A] =
    Effect.On(self, executor)

  final def repeat(times: Int): Effect[Unit] =
    times match {
      case t if t <= 0 => Effect.unit
      case t           => self and repeat(t - 1)
    }

  final def forever: Effect[Nothing] =
    self and self.forever

  final def interruptible: Effect[A] =
    Effect.SetInterruptible(self, true)

  final def uninterruptible: Effect[A] =
    Effect.SetInterruptible(self, false)

  final def unsafeRun(executor: ExecutionContextExecutor = Effect.defaultExecutor): Result[A] = {
    val latch  = new CountDownLatch(1)
    var result = null.asInstanceOf[Result[A]]
    val effect = self
      .fold(
        error => {
          result = Result.Error(error)
          latch.countDown()
        },
        value => {
          result = Result.Value(value)
          latch.countDown()
        }
      )
      .recover { throwable =>
        result = Result.UnexpectedError(throwable)
        latch.countDown()
      }

    Fiber(effect, executor, None)
    latch.await()
    result
  }
}

object Effect {
  private val defaultExecutor: ExecutionContextExecutor = ExecutionContext.global

  val unit: Effect[Unit] = Value(())

  def value[A](a: => A): Effect[A] = Value(a)

  def suspend[A](a: => A): Effect[A] = Suspend(() => a)

  def error[A](e: E): Effect[A] = Error(Right(e))

  private[effect] def unexpectedError[A](throwable: Throwable): Effect[A] = Error(Left(throwable))

  def callback[A](register: (Result[A] => Any) => Any): Effect[A] = Callback(register)

  private[effect] final case class Value[+A](value: A) extends Effect[A] {
    override def toString: String = s"Value($value)"
  }

  private[effect] final case class Suspend[+A](value: () => A) extends Effect[A] {
    override def toString: String = s"Suspend"
  }

  private[effect] final case class Error(error: Either[Throwable, E]) extends Effect[Nothing] {
    override def toString: String = s"Error($error)"
  }

  private[effect] object Interrupted extends Effect[Unit] {
    override def toString: String = s"Interrupted"
  }

  private[effect] final case class Callback[+A](register: (Result[A] => Any) => Any) extends Effect[A] {
    override def toString: String = s"Callback"
  }

  private[effect] final case class FlatMap[A, +B](effect: Effect[A], continuation: A => Effect[B]) extends Effect[B] {
    override def toString: String = s"FlatMap($effect)"
  }

  private[effect] final case class Fork[+A](effect: Effect[A]) extends Effect[Fiber[A]] {
    override def toString: String = s"Fork($effect)"
  }

  private[effect] final case class Fold[A, +B](effect: Effect[A], ifError: Either[Throwable, E] => Effect[B], ifValue: A => Effect[B])
      extends Effect[B]
      with (A => Effect[B]) {
    override def toString: String = s"Fold($effect)"

    override def apply(a: A): Effect[B] = ifValue(a)
  }

  private[effect] final case class On[A](effect: Effect[A], executor: ExecutionContextExecutor) extends Effect[A] {
    override def toString: String = s"On($effect, $executor)"
  }

  private[effect] final case class SetInterruptible[A](effect: Effect[A], isInterruptible: Boolean) extends Effect[A] {
    override def toString: String = s"SetInterruptible($effect, $isInterruptible)"
  }
}
