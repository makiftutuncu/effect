package effect

import java.util.concurrent.atomic.{AtomicLong, AtomicReference}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.util.control.NonFatal

sealed trait Fiber[+A] {
  def join: Effect[A]
}

object Fiber {
  private val fiberIds: AtomicLong = AtomicLong(0L)

  private[effect] def apply[A](effect: Effect[A], executor: ExecutionContextExecutor): Fiber[A] =
    Context(fiberIds.getAndIncrement(), effect, executor)

  private final case class Context[A](id: Long, startingEffect: Effect[A], startingExecutor: ExecutionContextExecutor) extends Fiber[A] {
    self =>
    private var looping: Boolean                                 = true
    private var instruction: Effect[Any]                         = startingEffect
    private var executor: ExecutionContextExecutor               = startingExecutor
    private val continuations: mutable.Stack[Any => Effect[Any]] = mutable.Stack.empty

    private val state: AtomicReference[State] = AtomicReference(State.Running(List.empty))

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
            callback(Result.Error(failedState.error))
            false
          },
          ifCompleted = { completedState =>
            callback(Result.Value(completedState.value))
            false
          }
        )
      }

    override def toString: String = s"Fiber($id, $startingEffect)"

    private enum State(val name: String) {
      case Running(callbacks: List[Result[A] => Any]) extends State("running")
      case Failed(error: Either[Throwable, E])        extends State("failed")
      case Completed(value: A)                        extends State("completed")
    }

    private inline def trace(message: String): Unit = println(s"[fiber-$id] $message")

    private inline def pauseLooping(): Unit = {
      looping = false
      trace("pause looping")
    }

    private inline def resumeLooping(): Unit = {
      looping = true
      trace("resume looping")
      loop()
    }

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
        failFiber(error)
      }
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
      }
    ): Unit = {
      var trying = true

      while (trying) {
        val oldState = state.get()

        oldState match {
          case s: State.Running   => trying = ifRunning(s)
          case s: State.Failed    => trying = ifFailed(s)
          case s: State.Completed => trying = ifCompleted(s)
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

    private inline def failFiber(error: Either[Throwable, E]): Unit =
      updateState(ifRunning = { runningState =>
        if (state.compareAndSet(runningState, State.Failed(error))) {
          trace(s"fail fiber: $error")
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
      try {
        while (looping) {
          instruction match {
            case Effect.Value(value) =>
              nextContinuationInLoop(value)

            case Effect.Suspend(getValue) =>
              nextContinuationInLoop(getValue())

            case Effect.Error(error) =>
              handleErrorOrFailLoop(error)

            case Effect.FlatMap(effect, continuation) =>
              continuations.push(continuation.asInstanceOf[Any => Effect[Any]])
              instruction = effect

            case Effect.Callback(register) =>
              pauseLooping()
              if (continuations.isEmpty) {
                register {
                  case Result.Error(error) => handleErrorOrFailLoop(error)
                  case Result.Value(value) => completeFiber(value.asInstanceOf[A])
                }
              } else {
                register {
                  case Result.Error(error) =>
                    handleErrorOrFailLoop(error)

                  case Result.Value(value) =>
                    instruction = Effect.Value(value)
                    resumeLooping()
                }
              }

            case Effect.Fork(effect) =>
              nextContinuationInLoop(Fiber(effect, executor))

            case fold @ Effect.Fold(effect, ifError, ifValue) =>
              continuations.push(fold.asInstanceOf[Any => Effect[Any]])
              instruction = effect

            case Effect.On(newExecutor) =>
              // TODO: Need to switch back to `executor` after `effect` is run.
              executor = newExecutor
              nextContinuationInLoop(())
          }
        }
      } catch {
        case NonFatal(throwable) =>
          handleErrorOrFailLoop(Left(throwable))
      }
  }
}
