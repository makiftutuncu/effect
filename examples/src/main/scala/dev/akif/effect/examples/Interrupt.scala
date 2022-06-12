package dev.akif.effect.examples

import effect.{Effect, EffectApp}

object Interrupt extends EffectApp {
  override def mainEffect(args: Array[String]): Effect[Any] =
    for {
      fiber <- (
        Effect.suspend(println("Can't stop me")).repeat(100).uninterruptible and
          Effect
            .suspend(println("Hello"))
            .forever
      )
        .finalize(Effect.value(println("Finalizing")))
        .fork
      _ <- Effect.value(Thread.sleep(10))
      _ <- fiber.interrupt
      _ <- Effect.value(println("Bye"))
    } yield ()
}
