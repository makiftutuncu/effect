package dev.akif.effect.examples

import effect.{Effect, EffectApp, Result}

object Callback extends EffectApp {
  override def effect(args: Array[String]): Effect[Any] =
    Effect.callback { complete =>
      println("Async started")
      Thread.sleep(100)
      println(s"Done")
      complete(Result.value(42))
    }
}
