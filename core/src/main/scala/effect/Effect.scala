package effect

import e.scala.E

import java.util.concurrent.CountDownLatch

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

/** A description of a functional effect that has following characteristics when run:
  *
  * <ul>
  *
  * <li>can produce a value of type A</li>
  *
  * <li>can fail with an error [[e.scala.E]]</li>
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

  /** Describes combining success values of this effect and given effect sequentially, using another effect
    *
    * @param that
    *   another effect to combine
    * @param f
    *   combination function
    *
    * @tparam B
    *   type of the value given effect can produce when successful
    * @tparam C
    *   type of the value combining effect can produce when successful
    *
    * @return
    *   an effect describing combining success values of this effect and given effect sequentially, using another effect
    */
  final def combineEffect[B, C](that: => Effect[B])(f: (A, B) => Effect[C]): Effect[C] =
    for {
      a <- self
      b <- that
      c <- f(a, b)
    } yield c

  /** Describes combining success values of this effect and given effect in parallel, using another effect
    *
    * <p>Parallelization here is achieved by forking effects.</p>
    *
    * @param that
    *   another effect to combine
    * @param f
    *   combination function
    *
    * @tparam B
    *   type of the value given effect can produce when successful
    * @tparam C
    *   type of the value combining effect can produce when successful
    *
    * @return
    *   an effect describing combining success values of this effect and given effect in parallel, using another effect
    *
    * @see
    *   [[effect.Effect.fork]]
    */
  final def combineParEffect[B, C](that: => Effect[B])(f: (A, B) => Effect[C]): Effect[C] =
    for {
      aFiber <- self.fork
      b      <- that
      a      <- aFiber.join
      c      <- f(a, b)
    } yield c

  /** Describes combining success values of this effect and given effect sequentially
    *
    * @param that
    *   another effect to combine
    * @param f
    *   combination function
    *
    * @tparam B
    *   type of the value given effect can produce when successful
    * @tparam C
    *   type of the value combining effect can produce when successful
    *
    * @return
    *   an effect describing combining success values of this effect and given effect sequentially
    */
  final def combine[B, C](that: => Effect[B])(f: (A, B) => C): Effect[C] =
    for {
      a <- self
      b <- that
    } yield {
      f(a, b)
    }

  /** Describes combining success values of this effect and given effect in parallel
    *
    * <p>Parallelization here is achieved by forking effects.</p>
    *
    * @param that
    *   another effect to combine
    * @param f
    *   combination function
    *
    * @tparam B
    *   type of the value given effect can produce when successful
    * @tparam C
    *   type of the value combining effect can produce when successful
    *
    * @return
    *   an effect describing combining success values of this effect and given effect in parallel
    *
    * @see
    *   [[effect.Effect.fork]]
    */
  final def combinePar[B, C](that: => Effect[B])(f: (A, B) => C): Effect[C] =
    for {
      aFiber <- self.fork
      b      <- that
      a      <- aFiber.join
    } yield {
      f(a, b)
    }

  /** Describes running this effect and also running given effect sequentially regardless of the result of this one, ignoring the result of
    * the given effect
    *
    * @param that
    *   another effect to run
    *
    * @tparam B
    *   type of the value given effect can produce when successful
    *
    * @return
    *   an effect describing running this effect and also running given effect sequentially regardless of the result of this one
    */
  final infix def also[B](that: => Effect[B]): Effect[A] =
    flatMap { a =>
      that.foldEffect(_ => Effect(a))
    }

  /** Describes running this effect and also running given effect in parallel regardless of the result of this one, ignoring the result of
    * the given effect
    *
    * <p>Parallelization here is achieved by forking effects.</p>
    *
    * @param that
    *   another effect to run
    *
    * @tparam B
    *   type of the value given effect can produce when successful
    *
    * @return
    *   an effect describing running this effect and also running given effect in parallel regardless of the result of this one
    * @see
    *   [[effect.Effect.fork]]
    */
  final infix def alsoPar[B](that: => Effect[B]): Effect[A] =
    flatMap { a =>
      that.fork.mapDiscarding(a)
    }

  /** Describes running this effect and running given effect sequentially, ignoring the result of this effect
    *
    * @param that
    *   another effect to run
    *
    * @tparam B
    *   type of the value given effect can produce when successful
    *
    * @return
    *   an effect describing running this effect and running given effect sequentially
    */
  final infix def and[B](that: => Effect[B]): Effect[B] =
    combine(that)((_, b) => b)

  /** Describes running this effect and running given effect in parallel, ignoring the result of this effect
    *
    * <p>Parallelization here is achieved by forking effects.</p>
    *
    * @param that
    *   another effect to run
    *
    * @tparam B
    *   type of the value given effect can produce when successful
    *
    * @return
    *   an effect describing running this effect and running given effect in parallel
    *
    * @see
    *   [[effect.Effect.fork]]
    */
  final infix def andPar[B](that: => Effect[B]): Effect[B] =
    combinePar(that)((_, b) => b)

  /** Describes combining success values of this effect and given effect sequentially into a tuple
    *
    * @param that
    *   another effect to combine
    *
    * @tparam B
    *   type of the value given effect can produce when successful
    *
    * @return
    *   an effect describing combining success values of this effect and given effect sequentially into a tuple
    */
  final infix def tuple[B](that: => Effect[B]): Effect[(A, B)] =
    combine(that)((a, b) => (a, b))

  /** Describes combining success values of this effect and given effect in parallel into a tuple
    *
    * <p>Parallelization here is achieved by forking effects.</p>
    *
    * @param that
    *   another effect to combine
    *
    * @tparam B
    *   type of the value given effect can produce when successful
    *
    * @return
    *   an effect describing combining success values of this effect and given effect in parallel into a tuple
    *
    * @see
    *   [[effect.Effect.fork]]
    */
  final infix def tuplePar[B](that: => Effect[B]): Effect[(A, B)] =
    combinePar(that)((a, b) => (a, b))

  /** Describes folding over the result of this effect, handling all possible outcomes to build a new effect
    *
    * @param handler
    *   handler function
    *
    * @tparam B
    *   type of the value resulting effect can produce when successful
    *
    * @return
    *   an effect describing folding over the result of this effect, handling all possible outcomes to build a new effect
    */
  final def foldEffect[B](handler: Result[A] => Effect[B]): Effect[B] =
    Effect.Fold(self, handler)

  /** Describes folding over the result of this effect, handling all possible outcomes to build an effect with a success value
    *
    * @param handler
    *   handler function
    *
    * @tparam B
    *   type of the value resulting effect can produce when successful
    *
    * @return
    *   an effect describing folding over the result of this effect, handling all possible outcomes to build an effect with a success value
    */
  final def fold[B](handler: Result[A] => B): Effect[B] =
    foldEffect(result => Effect(handler(result)))

  /** Describes handling error and unexpected error of this effect to build a new effect
    *
    * @param handler
    *   handler function
    *
    * @tparam AA
    *   type of the value resulting effect can produce when successful
    *
    * @return
    *   an effect describing handling error and unexpected error of this effect to build a new effect
    */
  final def handleAllErrorsEffect[AA >: A](handler: Either[Throwable, E] => Effect[AA]): Effect[AA] =
    foldEffect {
      case Result.Error(e)                   => handler(Right(e))
      case Result.UnexpectedError(throwable) => handler(Left(throwable))
      case result                            => Effect.callback(complete => complete(result))
    }

  /** Describes handling error and unexpected error of this effect to build an effect with a success value
    *
    * @param handler
    *   handler function
    *
    * @tparam AA
    *   type of the value resulting effect can produce when successful
    *
    * @return
    *   an effect describing handling error and unexpected error of this effect to build an effect with a success value
    */
  final def handleAllErrors[AA >: A](handler: Either[Throwable, E] => AA): Effect[AA] =
    handleAllErrorsEffect(e => Effect.Value(handler(e)))

  /** Describes handling unexpected error of this effect to build a new effect
    *
    * @param handler
    *   handler function
    *
    * @tparam AA
    *   type of the value resulting effect can produce when successful
    *
    * @return
    *   an effect describing handling unexpected error of this effect to build a new effect
    */
  final def handleUnexpectedErrorEffect[AA >: A](handler: Throwable => Effect[AA]): Effect[AA] =
    handleAllErrorsEffect {
      case Left(throwable) => handler(throwable)
      case Right(e)        => Effect.Error(Right(e))
    }

  /** Describes handling unexpected error of this effect to build an effect with a success value
    *
    * @param handler
    *   handler function
    *
    * @tparam AA
    *   type of the value resulting effect can produce when successful
    *
    * @return
    *   an effect describing handling unexpected error of this effect to build an effect with a success value
    */
  final def handleUnexpectedError[AA >: A](handler: Throwable => AA): Effect[AA] =
    handleUnexpectedErrorEffect(throwable => Effect.Value(handler(throwable)))

  /** Describes handling error of this effect to build a new effect
    *
    * @param handler
    *   handler function
    *
    * @tparam AA
    *   type of the value resulting effect can produce when successful
    *
    * @return
    *   an effect describing handling error of this effect to build a new effect
    */
  final def handleErrorEffect[AA >: A](handler: E => Effect[AA]): Effect[AA] =
    handleAllErrorsEffect {
      case Left(throwable) => Effect.Error(Left(throwable))
      case Right(e)        => handler(e)
    }

  /** Describes handling error of this effect to build an effect with a success value
    *
    * @param handler
    *   handler function
    *
    * @tparam AA
    *   type of the value resulting effect can produce when successful
    *
    * @return
    *   an effect describing handling error of this effect to build an effect with a success value
    */
  final def handleError[AA >: A](handler: E => AA): Effect[AA] =
    handleErrorEffect(e => Effect.Value(handler(e)))

  /** Describes converting error of this effect into another error to build a new effect failing with converted error
    *
    * @param mapper
    *   conversion function
    *
    * @return
    *   an effect describing converting error of this effect into another error to build a new effect failing with converted error
    */
  final def mapError(mapper: E => E): Effect[A] =
    handleAllErrorsEffect {
      case Left(throwable) => Effect.unexpectedError(throwable)
      case Right(e)        => Effect.error(mapper(e))
    }

  /** Describes converting unexpected error of this effect into another unexpected error to build a new effect failing unexpectedly with
    * converted unexpected error
    *
    * @param mapper
    *   conversion function
    *
    * @return
    *   an effect describing converting unexpected error of this effect into another unexpected error to build a new effect failing
    *   unexpectedly with converted unexpected error
    */
  final def mapUnexpectedError(mapper: Throwable => Throwable): Effect[A] =
    handleAllErrorsEffect {
      case Left(throwable) => Effect.unexpectedError(mapper(throwable))
      case Right(e)        => Effect.error(e)
    }

  /** Describes running given effect as a finalizer after this effect completes in any way
    *
    * @param finalizer
    *   an effect to run as a finalizer
    *
    * @return
    *   an effect describing running given effect runs as a finalizer after this effect completes in any way
    */
  final def ensuring(finalizer: => Effect[Any]): Effect[A] =
    foldEffect(result => finalizer and Effect.callback(complete => complete(result)))

  /** Describes shifting execution of this effect from current execution context to given execution context, then shifts it back
    *
    * @param executor
    *   a new execution context in which to run this effect
    *
    * @return
    *   an effect describing shifting execution of this effect from current execution context to given execution context, then shifting it
    *   back
    */
  final def on(executor: ExecutionContextExecutor): Effect[A] =
    Effect.On(self, executor)

  /** Describes repeating this effect given number of times, as long as it is successful, ignoring its success value
    *
    * @param times
    *   number of times this effect is to be repeated
    *
    * @return
    *   an effect describing repeating this effect given number of times, as long as it is successful, ignoring its success value
    */
  final def repeat(times: Int): Effect[Unit] =
    times match {
      case t if t <= 0 => Effect.unit
      case t           => self and repeat(t - 1)
    }

  /** Describes repeating this effect forever, as long as it is successful, ignoring its success value
    *
    * @return
    *   an effect describing repeating this effect forever, as long as it is successful, ignoring its success value
    */
  final def forever: Effect[Nothing] =
    self and self.forever

  /** Describes delaying execution of this effect for given duration in milliseconds
    *
    * @param millis
    *   duration for which to delay execution of this effect
    *
    * @return
    *   an effect describing delaying execution of this effect for given duration in milliseconds
    *
    * @see
    *   [[java.lang.Thread.sleep]]
    */
  final def delayed(millis: Long): Effect[A] =
    Effect(Thread.sleep(millis)) and self

  /** Describes this effect as an effect that cannot be interrupted
    *
    * @return
    *   an effect describing this effect as an effect that cannot be interrupted
    */
  final def uninterruptible: Effect[A] =
    Effect.SetUninterruptible(self)

  /** Starts execution of this effect, running potentially side-effecting and/or blocking code
    *
    * @param executor
    *   execution context in which this effect will run
    * @param traceEnabled
    *   whether to print debugging information as this effect runs
    *
    * @return
    *   result of the execution
    *
    * @see
    *   [[effect.Result]]
    */
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

  /** Successful effect yielding Unit value */
  val unit: Effect[Unit] = Value(())

  /** Creates an effect that, when successful, will produce given value, execution is suspended until the effect is run
    *
    * @param a
    *   success value to produce
    *
    * @tparam A
    *   type of the value to produce
    *
    * @return
    *   an effect that, when successful, will produce given value
    */
  def apply[A](a: => A): Effect[A] = Suspend(() => a)

  /** Creates an effect that fails with given error
    *
    * @param e
    *   error to produce
    *
    * @return
    *   an effect that fails with given error
    */
  def error(e: E): Effect[Nothing] = Error(Right(e))

  /** Creates an effect that fails with given unexpected error
    *
    * @param throwable
    *   unexpected error to produce
    *
    * @return
    *   an effect that fails with given unexpected error
    */
  def unexpectedError(throwable: Throwable): Effect[Nothing] = Error(Left(throwable))

  /** Creates an effect from a callback-based code, leaving completion logic to the caller to bring arbitrary asynchronous code into a
    * functional effect
    *
    * @param complete
    *   completion callback to call with a result
    *
    * @return
    *   an effect from a callback-based code
    */
  def callback[A](complete: (Result[A] => Any) => Any): Effect[A] = Callback(complete)

  private[effect] val defaultExecutor: ExecutionContextExecutor = ExecutionContext.global

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

  private[effect] final case class Callback[+A](complete: (Result[A] => Any) => Any) extends Effect[A] {
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
