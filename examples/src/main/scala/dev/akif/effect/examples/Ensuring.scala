package dev.akif.effect.examples

import effect.{E, Effect, EffectApp}

object Ensuring extends EffectApp {
  override def mainEffect(args: Array[String]): Effect[Any] =
    for {
      _ <- Effect(println("Hello")).ensuring(Effect(println("Bye 1")))
      _ <- Effect.error(E("test")).ensuring(Effect(println("Bye 2"))).handleAllErrors(_ => ())
      _ <- Effect.unexpectedError(new Exception).ensuring(Effect(println("Bye 3"))).handleAllErrors(_ => ())
      _ <- (
        for {
          _ <- Effect(println("Before"))
          fiber <- Effect {
            Thread.sleep(1000)
            println("After")
          }.fork
          _ <- fiber.interrupt
        } yield ()
      ).ensuring(Effect(println("Bye 4")))
    } yield ()
}
