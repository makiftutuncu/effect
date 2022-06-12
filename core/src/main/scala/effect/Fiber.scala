package effect

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

  private[effect] def apply[A](effect: Effect[A], executor: ExecutionContextExecutor, parentId: Option[Long]): Fiber[A] =
    Context(fiberIds.getAndIncrement(), effect, executor, parentId)

  private final case class Context[A](
    id: Long,
    startingEffect: Effect[A],
    startingExecutor: ExecutionContextExecutor,
    parentId: Option[Long]
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
      trace(s"start looping")
      loop()
    }

    override def join: Effect[A] =
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
          },
          ifInterrupted = { _ =>
            callback(Result.Interrupted)
            false
          }
        )
      }

    override def interrupt: Effect[Unit] = {
      trace("will interrupt")
      Effect.Value(interrupted.set(true))
    }

    override def toString: String = parentId.fold(s"Fiber($id, $startingEffect)")(parent => s"Fiber($parent/$id, $startingEffect)")

    private enum State(val name: String) {
      case Running(callbacks: List[Result[A] => Any]) extends State("running")
      case Failed(error: Either[Throwable, E])        extends State("failed")
      case Completed(value: A)                        extends State("completed")
      case Interrupted                                extends State("interrupted")
    }

    private inline def trace(message: String): Unit = println(s"[fiber ${parentId.fold(s"$id")(parent => s"$parent/$id")}] $message")

    private inline def nextContinuationInLoop(value: Any): Unit =
      if (continuations.isEmpty) {
        looping = false
        trace("stop looping")
        completeFiber(value.asInstanceOf[A])
      } else {
        val continuation = continuations.pop()
        trace(s"next continuation: $value")
        instruction = continuation(value)
      }

    private inline def handleErrorOrFailLoop(error: Either[Throwable, E]): Unit = {
      val nextFold = findNextFold()
      if (nextFold != null) {
        instruction = nextFold.ifError(error)
      } else {
        looping = false
        trace("fail looping")
        updateState(ifRunning = { runningState =>
          if (state.compareAndSet(runningState, State.Failed(error))) {
            trace(s"fail fiber: $error")
            false
          } else {
            true
          }
        })
      }
    }

    private inline def handleInterrupted(): Unit = {
      looping = false
      trace("interrupting")
      updateState(ifRunning = { runningState =>
        if (state.compareAndSet(runningState, State.Interrupted)) {
          trace("interrupted")
          false
        } else {
          true
        }
      })
    }

    private inline def updateState(
      ifRunning: State.Running => Boolean = { s =>
        throw new IllegalStateException(s"$self in ${s.name} state cannot be set to running!")
      },
      ifFailed: State.Failed => Boolean = { s =>
        throw new IllegalStateException(s"$self in ${s.name} state cannot be set to failed!")
      },
      ifCompleted: State.Completed => Boolean = { s =>
        throw new IllegalStateException(s"$self in ${s.name} state cannot be set to completed!")
      },
      ifInterrupted: State => Boolean = { s =>
        throw new IllegalStateException(s"$self in ${s.name} state cannot be set to interrupted!")
      }
    ): Unit = {
      var trying = true

      while (trying) {
        val oldState = state.get()

        oldState match {
          case s: State.Running   => trying = ifRunning(s)
          case s: State.Failed    => trying = ifFailed(s)
          case s: State.Completed => trying = ifCompleted(s)
          case s                  => trying = ifInterrupted(s)
        }
      }
    }

    private inline def completeFiber(value: A): Unit =
      updateState(ifRunning = { runningState =>
        if (state.compareAndSet(runningState, State.Completed(value))) {
          trace(s"complete fiber: $value")
          runningState.callbacks.foreach { callback => callback(Result.Value(value)) }
          false
        } else {
          true
        }
      })

    private inline def findNextFold(): Effect.Fold[Any, Any] = {
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
            isInterrupting.set(true)
            continuations.push(_ => instruction)
            instruction = Effect.Interrupted
          } else {
            instruction match {
              case Effect.Value(value) =>
                nextContinuationInLoop(value)

              case Effect.Suspend(getValue) =>
                nextContinuationInLoop(getValue())

              case Effect.Error(error) =>
                handleErrorOrFailLoop(error)

              case Effect.Interrupted =>
                handleInterrupted()

              case Effect.FlatMap(effect, continuation) =>
                continuations.push(continuation.asInstanceOf[Any => Effect[Any]])
                instruction = effect

              case Effect.Callback(register) =>
                looping = false
                trace("pause looping")
                register {
                  case Result.UnexpectedError(throwable) =>
                    handleErrorOrFailLoop(Left(throwable))

                  case Result.Interrupted =>
                    handleInterrupted()

                  case Result.Error(e) =>
                    handleErrorOrFailLoop(Right(e))

                  case Result.Value(value) =>
                    if (continuations.isEmpty) {
                      completeFiber(value.asInstanceOf[A])
                    } else {
                      instruction = Effect.Value(value)
                      looping = true
                      trace("resume looping")
                    }
                }

              case Effect.Fork(effect) =>
                trace("forking a new fiber")
                nextContinuationInLoop(Fiber(effect, executor, Some(id)))

              case fold @ Effect.Fold(effect, _, _) =>
                continuations.push(fold.asInstanceOf[Any => Effect[Any]])
                instruction = effect

              case Effect.On(effect, newExecutor) =>
                val oldExecutor = executor
                trace(s"switching executor to $newExecutor")
                executor = newExecutor
                instruction = effect.finalize(Effect.Value {
                  trace(s"switching executor back to $oldExecutor")
                  executor = oldExecutor
                })

              case Effect.SetInterruptible(effect, interruptible) =>
                val oldInterruptible = isInterruptible.get
                trace(s"setting interruptible to $interruptible")
                isInterruptible.set(interruptible)
                instruction = effect.finalize(Effect.Value({
                  trace(s"setting interruptible back to $oldInterruptible")
                  isInterruptible.set(oldInterruptible)
                }))
            }
          }
        } catch {
          case NonFatal(throwable) =>
            handleErrorOrFailLoop(Left(throwable))
        }
      }
  }
}
