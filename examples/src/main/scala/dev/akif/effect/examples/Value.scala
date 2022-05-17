package dev.akif.effect.examples

import effect.{Effect, EffectApp}

object Value extends EffectApp {
  override def effect(args: Array[String]): Effect[Any] =
    Effect.value(42)
}
