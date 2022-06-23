package dev.akif.effect.examples

import effect.{Effect, EffectApp}

object Suspend extends EffectApp {
  override def mainEffect(args: Array[String]): Effect[Any] =
    Effect(println("Hello world!"))
}
