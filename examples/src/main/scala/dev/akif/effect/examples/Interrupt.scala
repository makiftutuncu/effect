package dev.akif.effect.examples

import effect.{Effect, EffectApp}

object Interrupt extends EffectApp {
  override def mainEffect(args: Array[String]): Effect[Any] =
    for {
      fiber <- (
        Effect(println("Can't stop me")).repeat(100).uninterruptible and
          Effect(println("Hello")).forever
      )
        .ensuring(Effect(println("Finalizing")))
        .fork
      _ <- Effect(Thread.sleep(40))
      _ <- fiber.interrupt
      _ <- Effect(println("Bye"))
    } yield ()
}
