package effect

import java.util.concurrent.atomic.AtomicReference

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

sealed trait Fiber[+A] {
  def start(): Unit
  def join: Effect[A]
}

object Fiber {
  private[effect] final case class Context[A](effect: Effect[A]) extends Fiber[A] { self =>
    private[effect] enum State {
      case Running(callbacks: List[Result[A] => Any])
      case Failed(error: Either[Throwable, E])
      case Completed(value: A)
    }

    private val state: AtomicReference[State] = AtomicReference(State.Running(List.empty))

    private def complete(value: A): Unit = {
      var running = true

      while (running) {
        val oldState = state.get()

        oldState match {
          case State.Running(callbacks) =>
            if (state.compareAndSet(oldState, State.Completed(value))) {
              callbacks.foreach { callback => callback(Result.value(value)) }
              running = false
            }

          case State.Failed(_) =>
            throw new IllegalStateException("Failed fiber cannot be completed with a value!")

          case State.Completed(_) =>
            throw new IllegalStateException("Fiber cannot be completed more than once!")
        }
      }
    }

    private def await(callback: A => Any): Unit = {
      var running = true

      while (running) {
        val oldState = state.get()

        oldState match {
          case State.Running(callbacks) =>
            val newCallback: Result[A] => Any = {
              case Result.Error(error) => fail(error)
              case Result.Value(value) => callback(value)
            }

            running = !state.compareAndSet(oldState, State.Running(callbacks :+ newCallback))

          case State.Completed(value) =>
            callback(value)
            running = false

          case State.Failed(_) =>
            throw new IllegalStateException("Failed fiber cannot be awaited!")
        }
      }
    }

    private def fail(error: Either[Throwable, E]): Unit = {
      var running = true

      while (running) {
        val oldState = state.get()

        oldState match {
          case State.Completed(value) =>
            throw new IllegalStateException("Completed fiber cannot be failed!")

          case State.Running(callbacks) =>
            running = !state.compareAndSet(oldState, State.Failed(error))

          case State.Failed(error) =>
            throw new IllegalStateException("Failed fiber cannot be failed again!")
        }
      }
    }

    override def start(): Unit =
      ExecutionContext.global.execute { () =>
        effect.unsafeRun {
          case Result.Value(value) => complete(value)
          case Result.Error(error) => fail(error)
        }
      }

    override def join: Effect[A] = Effect.callback(await)
  }
}
