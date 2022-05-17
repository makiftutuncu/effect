package dev.akif.effect.examples

import effect.{Effect, EffectApp}

object Suspend extends EffectApp {
  override def effect(args: Array[String]): Effect[Any] =
    Effect.suspend(println("Hello world!"))
}
