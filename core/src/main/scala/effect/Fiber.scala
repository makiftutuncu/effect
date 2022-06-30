package effect

import java.time.Instant
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.util.control.NonFatal

sealed trait Fiber[+A] {
  def join: Effect[A]

  def interrupt: Effect[Unit]
}

object Fiber {
  private val fiberIds: AtomicLong = AtomicLong(0L)

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
    private var looping: Boolean                                 = true
    private var instruction: Effect[Any]                         = startingEffect
    private var executor: ExecutionContextExecutor               = startingExecutor
    private val continuations: mutable.Stack[Any => Effect[Any]] = mutable.Stack.empty

    private val state: AtomicReference[State]  = AtomicReference(State.Running(List.empty))
    private val interrupted: AtomicBoolean     = AtomicBoolean(false)
    private val isInterrupting: AtomicBoolean  = AtomicBoolean(false)
    private val isInterruptible: AtomicBoolean = AtomicBoolean(true)

    executor.execute { () =>
      trace(s"created")
      setLooping(true)
      loop()
    }

    override def join: Effect[A] = {
      trace("join called")
      Effect.callback { callback =>
        updateState(
          ifRunning = { runningState =>
            !state.compareAndSet(runningState, State.Running(runningState.callbacks :+ callback))
          },
          ifFailed = { failedState =>
            failedState.error match {
              case Left(throwable) => callback(Result.UnexpectedError(throwable))
              case Right(e)        => callback(Result.Error(e))
            }
            false
          },
          ifCompleted = { completedState =>
            callback(Result.Value(completedState.value))
            false
          }
        )
      }
    }

    override def interrupt: Effect[Unit] = {
      trace("interrupt called")
      Effect(interrupted.set(true))
    }

    override def toString: String = parentId.fold(s"Fiber($id, $startingEffect)")(parent => s"Fiber($parent/$id, $startingEffect)")

    private enum State(val name: String) {
      case Running(callbacks: List[Result[A] => Any]) extends State("running")
      case Failed(error: Either[Throwable, E])        extends State("failed")
      case Completed(value: A)                        extends State("completed")
    }

    private def trace(message: String): Unit =
      if (traceEnabled) println(s"[${Instant.now}] [F${parentId.fold(s"$id")(parent => s"$parent/$id")}] $message")

    private def setLooping(looping: Boolean): Unit = {
      trace(s"setting looping to $looping")
      self.looping = looping
    }

    private def nextContinuationInLoop(value: Any): Unit =
      if (continuations.isEmpty) {
        setLooping(false)
        completeFiber(value.asInstanceOf[A])
      } else {
        val continuation = continuations.pop()
        instruction = continuation(value)
      }

    private def handleErrorOrFailLoop(error: Either[Throwable, E]): Unit = {
      val nextFold = findNextFold()
      if (nextFold != null) {
        trace("handling error")
        instruction = nextFold.ifError(error)
        trace("handled error")
      } else {
        setLooping(false)
        trace("no error handlers found, failing")
        updateState(ifRunning = { runningState =>
          !state.compareAndSet(runningState, State.Failed(error))
        })
      }
    }

    private def handleInterrupted(): Unit = {
      val nextFold = findNextFold()
      if (nextFold != null) {
        trace("running finalizer after interruption")
        instruction = nextFold.ifValue(())
        trace("ran finalizer after interruption")
      } else {
        trace("no finalizers found")
        setLooping(false)
      }
    }

    private def updateState(
      ifRunning: State.Running => Boolean = { s =>
        throw IllegalStateException(s"$self in ${s.name} state cannot be set to running!")
      },
      ifFailed: State.Failed => Boolean = { s =>
        throw IllegalStateException(s"$self in ${s.name} state cannot be set to failed!")
      },
      ifCompleted: State.Completed => Boolean = { s =>
        throw IllegalStateException(s"$self in ${s.name} state cannot be set to completed!")
      }
    ): Unit = {
      var trying = true

      trace(s"updating fiber state from ${state.get()}")

      while (trying) {
        val oldState = state.get()

        oldState match {
          case s: State.Running   => trying = ifRunning(s)
          case s: State.Failed    => trying = ifFailed(s)
          case s: State.Completed => trying = ifCompleted(s)
        }
      }

      trace(s"updated fiber state to ${state.get()}")
    }

    private def completeFiber(value: A): Unit = {
      trace(s"completing fiber with value $value")
      updateState(ifRunning = { runningState =>
        if (state.compareAndSet(runningState, State.Completed(value))) {
          runningState.callbacks.foreach { callback => callback(Result.Value(value)) }
          false
        } else {
          true
        }
      })
    }

    private def findNextFold(): Effect.Fold[Any, Any] = {
      var trying       = true
      var continuation = null.asInstanceOf[Any => Effect[Any]]

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
      while (looping) {
        try {
          if (interrupted.get && isInterruptible.get && !isInterrupting.get) {
            trace("starting to interrupt")
            isInterrupting.set(true)
            continuations.push(_ => instruction)
            instruction = Effect.Interrupted
          } else {
            if (interrupted.get && isInterrupting.get) {
              trace("already interrupting")
            } else if (interrupted.get) {
              trace("cannot start to interrupting in uninterruptible region")
            }
            instruction match {
              case Effect.Value(value) =>
                trace(s"processing value: $value")
                nextContinuationInLoop(value)

              case Effect.Suspend(getValue) =>
                val value = getValue()
                trace(s"processing suspend: $value")
                nextContinuationInLoop(value)

              case Effect.Error(error) =>
                trace(s"processing error: $error")
                handleErrorOrFailLoop(error)

              case Effect.Interrupted =>
                trace("processing interrupted")
                handleInterrupted()

              case Effect.FlatMap(effect, continuation) =>
                trace(s"processing flatmap")
                continuations.push(continuation.asInstanceOf[Any => Effect[Any]])
                instruction = effect

              case Effect.Callback(register) =>
                trace("processing callback")
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
                      completeFiber(value.asInstanceOf[A])
                    } else {
                      instruction = Effect.Value(value)
                      setLooping(true)
                    }
                }

              case Effect.Fork(effect) =>
                trace("processing fork")
                nextContinuationInLoop(Fiber(effect, executor, Some(id), traceEnabled))

              case fold @ Effect.Fold(effect, _, _) =>
                trace("processing fold")
                continuations.push(fold.asInstanceOf[Any => Effect[Any]])
                instruction = effect

              case Effect.On(effect, newExecutor) =>
                trace("processing on")
                val oldExecutor = executor
                trace(s"switching executor to $newExecutor")
                executor = newExecutor
                instruction = effect.ensuring(Effect {
                  trace(s"switching executor back to $oldExecutor")
                  executor = oldExecutor
                })

              case Effect.SetInterruptible(effect, interruptible) =>
                trace(s"processing set interruptible")
                val oldInterruptible = isInterruptible.get
                trace(s"setting interruptible to $interruptible")
                isInterruptible.set(interruptible)
                instruction = effect.ensuring(Effect {
                  trace(s"setting interruptible back to $oldInterruptible")
                  isInterruptible.set(oldInterruptible)
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
