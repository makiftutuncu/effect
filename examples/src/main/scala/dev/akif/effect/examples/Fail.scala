package dev.akif.effect.examples

import effect.{E, Effect, EffectApp}

object Fail extends EffectApp {
  override def mainEffect(args: Array[String]): Effect[Any] =
    Effect.error(E(1, "error"))
}
