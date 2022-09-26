package effect

import java.util.concurrent.CountDownLatch

import scala.annotation.{tailrec, targetName}
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.util.control.NonFatal

/** A description of a functional effect that has following characteristics when run:
  *
  * <ul>
  *
  * <li>can produce a value of type A</li>
  *
  * <li>can fail with an error [[effect.E]]</li>
  *
  * <li>can fail unexpectedly with a [[java.lang.Throwable]]</li>
  *
  * <li>can be interrupted</li>
  *
  * </ul>
  *
  * @tparam A
  *   type of the value this effect can produce when successful
  */
sealed trait Effect[+A] { self =>

  /** Describes running given effect after this effect using its success value
    *
    * @param f
    *   another effect to run
    *
    * @tparam B
    *   type of the value given effect can produce when successful
    *
    * @return
    *   an effect of running given effect after this effect using its success value
    */
  final def flatMap[B](f: A => Effect[B]): Effect[B] =
    Effect.FlatMap(self, f)

  /** Describes converting the success value of this effect into another value
    *
    * @param f
    *   function to convert the success value of this effect to another value
    *
    * @tparam B
    *   type of the value to be converted
    *
    * @return
    *   an effect of converting the success value of this effect into another value
    */
  final def map[B](f: A => B): Effect[B] =
    flatMap(a => Effect.Value(f(a)))

  /** Describes replacing the success value of this effect with another value, discarding the original success value
    *
    * @param b
    *   new success value to be used
    *
    * @tparam B
    *   type of the new success value
    *
    * @return
    *   an effect of replacing the success value of this effect with another value
    */
  final def mapDiscarding[B](b: => B): Effect[B] =
    map(_ => b)

  /** Describes replacing the success value of this effect with Unit, discarding the original success value
    *
    * @return
    *   an effect of replacing the success value of this effect with Unit
    */
  final def unit: Effect[Unit] =
    mapDiscarding(())

  /** Describes forking the computation into another [[effect.Fiber]], kicking off computation asynchronously
    *
    * @return
    *   an effect of forking the computation into another [[effect.Fiber]]
    */
  final def fork: Effect[Fiber[A]] =
    Effect.Fork(self)

  final def combineEffect[B, C](that: => Effect[B])(f: (A, B) => Effect[C]): Effect[C] =
    for {
      a <- self
      b <- that
      c <- f(a, b)
    } yield c

  final def combineParEffect[B, C](that: => Effect[B])(f: (A, B) => Effect[C]): Effect[C] =
    for {
      aFiber <- self.fork
      b      <- that
      a      <- aFiber.join
      c      <- f(a, b)
    } yield c

  final def combine[B, C](that: => Effect[B])(f: (A, B) => C): Effect[C] =
    for {
      a <- self
      b <- that
    } yield {
      f(a, b)
    }

  final def combinePar[B, C](that: => Effect[B])(f: (A, B) => C): Effect[C] =
    for {
      aFiber <- self.fork
      b      <- that
      a      <- aFiber.join
    } yield {
      f(a, b)
    }

  final def also[B](that: => Effect[B]): Effect[A] =
    flatMap { a =>
      that.foldEffect(_ => Effect(a))
    }

  final def alsoPar[B](that: => Effect[B]): Effect[A] =
    flatMap { a =>
      that.fork.mapDiscarding(a)
    }

  final def and[B](that: => Effect[B]): Effect[B] =
    combine(that)((_, b) => b)

  final def andPar[B](that: => Effect[B]): Effect[B] =
    combinePar(that)((_, b) => b)

  final def tuple[B](that: => Effect[B]): Effect[(A, B)] =
    combine(that)((a, b) => (a, b))

  final def tuplePar[B](that: => Effect[B]): Effect[(A, B)] =
    combinePar(that)((a, b) => (a, b))

  final def foldEffect[B](handler: Result[A] => Effect[B]): Effect[B] =
    Effect.Fold(self, handler)

  final def fold[B](handler: Result[A] => B): Effect[B] =
    foldEffect(result => Effect(handler(result)))

  final def handleAllErrorsEffect[AA >: A](handler: Either[Throwable, E] => Effect[AA]): Effect[AA] =
    foldEffect {
      case Result.Value(a)                   => Effect(a)
      case Result.Error(e)                   => handler(Right(e))
      case Result.Interrupted                => Effect.callback(callback => callback(Result.Interrupted))
      case Result.UnexpectedError(throwable) => handler(Left(throwable))
    }

  final def handleAllErrors[AA >: A](handler: Either[Throwable, E] => AA): Effect[AA] =
    handleAllErrorsEffect(e => Effect.Value(handler(e)))

  final def handleUnexpectedErrorEffect[AA >: A](handler: Throwable => Effect[AA]): Effect[AA] =
    handleAllErrorsEffect {
      case Left(throwable) => handler(throwable)
      case Right(e)        => Effect.Error(Right(e))
    }

  final def handleUnexpectedError[AA >: A](handler: Throwable => AA): Effect[AA] =
    handleUnexpectedErrorEffect(throwable => Effect.Value(handler(throwable)))

  final def handleErrorEffect[AA >: A](handler: E => Effect[AA]): Effect[AA] =
    handleAllErrorsEffect {
      case Left(throwable) => Effect.Error(Left(throwable))
      case Right(e)        => handler(e)
    }

  final def handleError[AA >: A](handler: E => AA): Effect[AA] =
    handleErrorEffect(e => Effect.Value(handler(e)))

  final def mapError(mapper: E => E): Effect[A] =
    handleAllErrorsEffect {
      case Left(throwable) => Effect.unexpectedError(throwable)
      case Right(e)        => Effect.error(mapper(e))
    }

  final def mapUnexpectedError(mapper: Throwable => Throwable): Effect[A] =
    handleAllErrorsEffect {
      case Left(throwable) => Effect.unexpectedError(mapper(throwable))
      case Right(e)        => Effect.error(e)
    }

  final def ensuring(finalizer: => Effect[Any]): Effect[A] =
    foldEffect {
      case Result.Value(a)                   => finalizer.mapDiscarding(a)
      case Result.Error(e)                   => finalizer and Effect.error(e)
      case Result.Interrupted                => finalizer and Effect.callback(callback => callback(Result.Interrupted))
      case Result.UnexpectedError(throwable) => finalizer and Effect.unexpectedError(throwable)
    }

  final def on(executor: ExecutionContextExecutor): Effect[A] =
    Effect.On(self, executor)

  final def repeat(times: Int): Effect[Unit] =
    times match {
      case t if t <= 0 => Effect.unit
      case t           => self and repeat(t - 1)
    }

  final def forever: Effect[Nothing] =
    self and self.forever

  final def delayed(millis: Long): Effect[A] =
    Effect(Thread.sleep(millis)) and self

  final def uninterruptible: Effect[A] =
    Effect.SetUninterruptible(self)

  final def unsafeRun(executor: ExecutionContextExecutor = Effect.defaultExecutor, traceEnabled: Boolean = false): Result[A] = {
    val latch  = new CountDownLatch(1)
    var result = null.asInstanceOf[Result[A]]
    val effect = self.fold {
      case Result.Value(a) =>
        result = Result.Value(a)
        latch.countDown()

      case Result.Error(e) =>
        result = Result.Error(e)
        latch.countDown()

      case Result.Interrupted =>
        result = Result.Interrupted
        latch.countDown()

      case Result.UnexpectedError(throwable) =>
        result = Result.UnexpectedError(throwable)
        latch.countDown()
    }
    Fiber(effect, executor, None, traceEnabled)
    latch.await()
    result
  }
}

object Effect {
  private val defaultExecutor: ExecutionContextExecutor = ExecutionContext.global

  val unit: Effect[Unit] = Value(())

  def apply[A](a: => A): Effect[A] = Suspend(() => a)

  def error(e: E): Effect[Nothing] = Error(Right(e))

  def unexpectedError(throwable: Throwable): Effect[Nothing] = Error(Left(throwable))

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

  private[effect] final case class Fold[A, +B](effect: Effect[A], handler: Result[A] => Effect[B]) extends Effect[B] with (A => Effect[B]) {
    override def toString: String = s"Fold($effect)"

    override def apply(a: A): Effect[B] =
      handler(Result.Value(a))
  }

  private[effect] final case class On[A](effect: Effect[A], executor: ExecutionContextExecutor) extends Effect[A] {
    override def toString: String = s"On($executor)"
  }

  private[effect] final case class SetUninterruptible[A](effect: Effect[A]) extends Effect[A] {
    override def toString: String = s"SetUninterruptible($effect)"
  }
}
