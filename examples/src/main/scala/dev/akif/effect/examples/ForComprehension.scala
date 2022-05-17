package dev.akif.effect.examples

import effect.{Effect, EffectApp, Result}

object ForComprehension extends EffectApp {
  override def effect(args: Array[String]): Effect[Any] =
    for {
      int      <- Effect.value(42)
      string   <- Effect.value("hello")
      combined <- Effect.suspend(string + int)
      asyncResult <- Effect.callback { complete =>
        println("Async started")
        Thread.sleep(100)
        println(s"Done: $combined")
        complete(Result.value(1))
      }
      result <- Effect.value(s"Result is $asyncResult")
    } yield {
      result
    }
}
