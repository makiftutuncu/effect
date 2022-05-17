package dev.akif.effect.examples

import effect.{Effect, EffectApp}

object FailUnexpectedly extends EffectApp {
  override def effect(args: Array[String]): Effect[Any] =
    Effect.suspend {
      throw new Exception("boom")
    }
}
