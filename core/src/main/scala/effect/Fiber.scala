package effect

import java.time.Instant
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.util.control.NonFatal

/** An asynchronous computation that is already running
  *
  * @tparam A
  *   type of value this computation can produce when successful
  */
sealed trait Fiber[+A] {

  /** Describe the effect of getting this fiber's result, waiting until it completes if necessary
    *
    * @return
    *   an effect describing getting this fiber's result
    */
  def join: Effect[A]

  /** Describe the effect of interrupting this fiber's computation
    *
    * @return
    *   an effect describing interrupting this fiber's computation
    */
  def interrupt: Effect[Unit]
}

object Fiber {
  private val fiberIds: AtomicLong = AtomicLong(0)

  private type Continuation = Any => Effect[Any]

  private[effect] def apply[A](
    effect: Effect[A],
    executor: ExecutionContextExecutor,
    parentId: Option[Long],
    traceEnabled: Boolean
  ): Fiber[A] =
    Context(fiberIds.getAndIncrement(), effect, executor, parentId, traceEnabled)

  private final case class Context[A](
    id: Long,
    startingEffect: Effect[A],
    startingExecutor: ExecutionContextExecutor,
    parentId: Option[Long],
    traceEnabled: Boolean
  ) extends Fiber[A] {
    self =>
    private var instruction: Effect[Any]                   = startingEffect
    private val continuations: mutable.Stack[Continuation] = mutable.Stack.empty

    private val looping: AtomicBoolean                              = AtomicBoolean(true)
    private val executor: AtomicReference[ExecutionContextExecutor] = AtomicReference(startingExecutor)
    private val state: AtomicReference[State]                       = AtomicReference(State.Running(List.empty))
    private val interrupted: AtomicBoolean                          = AtomicBoolean(false)
    private val isInterrupting: AtomicBoolean                       = AtomicBoolean(false)
    private val isInterruptible: AtomicBoolean                      = AtomicBoolean(true)
    private val instructionCounter: AtomicLong                      = AtomicLong(0)

    executor.get().execute { () =>
      trace("created")
      setLooping(true)
    }

    override def join: Effect[A] = {
      trace("got join call")
      Effect.callback { complete =>
        updateStateWhen {
          case State.Running(existingCallbacks) =>
            trace("adding a new callback to be notified when completed")
            Some(State.Running(existingCallbacks :+ complete))

          case State.Completed(result) =>
            trace("joining")
            complete(result)
            None
        }
      }
    }

    override def interrupt: Effect[Unit] = {
      trace("got interrupt call")
      Effect(interrupted.set(true))
    }

    override def toString: String = s"Fiber($idString, $startingEffect)"

    private lazy val idString: String = parentId.fold(s"$id")(parent => s"$parent/$id")

    private enum State(val name: String) {
      case Running(callbacks: List[Result[A] => Any]) extends State("running")
      case Completed(result: Result[A])               extends State("completed")

      override def toString: String = name

      val isRunning: Boolean = this.isInstanceOf[Running]
    }

    private def trace(message: String): Unit =
      if (traceEnabled) println(s"[${Instant.now}] [F$idString] [I${instructionCounter.get}] $message")

    private def setLooping(looping: Boolean): Unit = {
      val oldLooping = self.looping.getAndSet(looping)
      if (oldLooping != looping) {
        trace(s"setting looping to $looping")
      }
      if (looping) {
        loop()
      }
    }

    private def switchToExecutor(executor: ExecutionContextExecutor): Unit = {
      trace(s"switching executor to $executor")
      setLooping(false)
      self.executor.set(executor)
      executor.execute(() => {
        setLooping(true)
      })
    }

    private def updateStateWhen(stateMatcher: PartialFunction[State, Option[State]]): Unit = {
      var trying = true
      while (trying) {
        val oldState = state.get()
        stateMatcher.unapply(oldState) match {
          case None =>
            throw IllegalStateException(s"$self was in $oldState which was unexpected!")

          case Some(None) =>
            trying = false

          case Some(Some(newState)) =>
            val updated = state.compareAndSet(oldState, newState)
            trying = !updated
            if (updated) {
              trace(s"updated state from $oldState to $newState")
            }
        }
      }
    }

    private def nextContinuationInLoop(value: Any): Unit =
      if (continuations.isEmpty) {
        trace("no more continuations")
        completeFiber(Result.Value(value.asInstanceOf[A]))
      } else {
        val continuation = continuations.pop()
        instruction = continuation(value)
      }

    private def handleErrorOrFailLoop(error: Either[Throwable, E]): Unit = {
      val nextFold = findNextFold()
      val result   = error.fold(Result.UnexpectedError.apply, Result.Error.apply)
      if (nextFold != null) {
        trace("found error handler, handling")
        instruction = nextFold.handler(result)
        setLooping(true)
      } else {
        trace("no error handlers found, failing")
        completeFiber(result)
      }
    }

    private def handleInterrupted(): Unit = {
      val nextFold = findNextFold()
      val result   = Result.Interrupted
      if (nextFold != null) {
        trace("running finalizer")
        instruction = nextFold.handler(result)
        setLooping(true)
      } else {
        trace("no finalizers found")
        completeFiber(result)
      }
    }

    private def completeFiber(result: Result[A]): Unit = {
      trace(s"completing fiber with result $result")
      setLooping(false)
      updateStateWhen { case State.Running(callbacks) =>
        callbacks.foreach(complete => complete(result))
        Some(State.Completed(result))
      }
    }

    private def findNextFold(): Effect.Fold[Any, Any] = {
      var trying       = true
      var continuation = null.asInstanceOf[Continuation]

      while (trying) {
        if (continuations.isEmpty) {
          trying = false
        } else {
          continuation = continuations.pop()
          trying = !continuation.isInstanceOf[Effect.Fold[_, _]]
        }
      }

      continuation.asInstanceOf[Effect.Fold[Any, Any]]
    }

    private def loop(): Unit =
      while (looping.get() && state.get().isRunning) {
        instructionCounter.incrementAndGet()
        trace(s"instruction: $instruction")
        try {
          if (interrupted.get && isInterruptible.get && !isInterrupting.get) {
            trace("starting to interrupt")
            isInterrupting.set(true)
            continuations.push(_ => instruction)
            instruction = Effect.Interrupted
          } else {
            instruction match {
              case Effect.Value(value) =>
                nextContinuationInLoop(value)

              case Effect.Suspend(getValue) =>
                val value = getValue()
                nextContinuationInLoop(value)

              case Effect.Error(error) =>
                handleErrorOrFailLoop(error)

              case Effect.Interrupted =>
                handleInterrupted()

              case Effect.FlatMap(effect, continuation) =>
                continuations.push(continuation.asInstanceOf[Continuation])
                instruction = effect

              case Effect.Callback(register) =>
                setLooping(false)
                register {
                  case Result.UnexpectedError(throwable) =>
                    trace(s"callback completed: unexpected error $throwable")
                    handleErrorOrFailLoop(Left(throwable))

                  case Result.Interrupted =>
                    trace("callback completed: interrupted")
                    handleInterrupted()

                  case Result.Error(e) =>
                    trace(s"callback completed: error $e")
                    handleErrorOrFailLoop(Right(e))

                  case Result.Value(value) =>
                    trace(s"callback completed: $value")
                    if (continuations.isEmpty) {
                      completeFiber(Result.Value(value.asInstanceOf[A]))
                    } else {
                      instruction = Effect.Value(value)
                      setLooping(true)
                    }
                }

              case Effect.Fork(effect) =>
                val fiber = Fiber(effect, executor.get(), Some(id), traceEnabled)
                nextContinuationInLoop(fiber)

              case fold @ Effect.Fold(effect, _) =>
                continuations.push(fold.asInstanceOf[Continuation])
                instruction = effect

              case Effect.On(effect, newExecutor) =>
                val oldExecutor = executor.get()
                trace(s"switching to executor $newExecutor")
                switchToExecutor(newExecutor)
                instruction = effect.ensuring(Effect {
                  trace(s"switching back to executor $oldExecutor")
                  switchToExecutor(oldExecutor)
                })

              case Effect.SetUninterruptible(effect) =>
                trace("setting interruptible to false")
                isInterruptible.set(false)
                instruction = effect.ensuring(Effect {
                  trace("setting interruptible back to true")
                  isInterruptible.set(true)
                })
            }
          }
        } catch {
          case NonFatal(throwable) =>
            trace(s"caught unexpected error $throwable")
            handleErrorOrFailLoop(Left(throwable))
        }
      }
  }
}
