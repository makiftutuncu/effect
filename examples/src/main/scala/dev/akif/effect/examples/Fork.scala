package dev.akif.effect.examples

import effect.{Effect, EffectApp}

import e.scala.E

object Fork extends EffectApp {
  val program1: Effect[String] = Effect("hello")
  val program2: Effect[Int]    = Effect.error(E.name("error"))

  override def mainEffect(args: Array[String]): Effect[Any] =
    for {
      fiber1  <- program1.fork
      fiber2  <- program2.fork
      _       <- Effect(println("Forked"))
      result1 <- fiber1.join
      result2 <- fiber2.join
    } yield {
      result1 -> result2
    }
}
