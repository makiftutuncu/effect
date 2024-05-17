package dev.akif.effect.examples

import effect.{Effect, EffectApp}

import e.scala.E

object Ensuring extends EffectApp {
  override def mainEffect(args: Array[String]): Effect[Any] =
    for {
      _ <- Effect(println("Hello")).ensuring(Effect(println("Bye 1")))
      _ <- Effect.error(E.name("test")).ensuring(Effect(println("Bye 2"))).handleAllErrors(_ => ())
      _ <- Effect.unexpectedError(new Exception).ensuring(Effect(println("Bye 3"))).handleAllErrors(_ => ())
      _ <- (
        for {
          _     <- Effect(println("Before"))
          fiber <- (Effect.unit.delayed(1000) and Effect(println("After"))).fork
          _     <- fiber.interrupt
        } yield ()
      ).ensuring(Effect(println("Bye 4")))
    } yield ()
}
