package dev.akif.effect.examples

import effect.{Effect, EffectApp}

object Value extends EffectApp {
  override def mainEffect(args: Array[String]): Effect[Any] =
    Effect(42)
}
