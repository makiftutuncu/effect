package dev.akif.effect.examples

import effect.{E, Effect, EffectApp}

object Fork extends EffectApp {
  val program1: Effect[String] = Effect.value("hello")

  val program2: Effect[Int] =
    Effect.error(E(1, "error"))

  override def effect(args: Array[String]): Effect[Any] =
    for {
      fiber1  <- program1.fork
      fiber2  <- program2.fork
      _       <- Effect.suspend(println("Forked"))
      result1 <- fiber1.join
      result2 <- fiber2.join
    } yield {
      result1 -> result2
    }
}
