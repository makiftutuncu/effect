package dev.akif.effect.examples

import effect.{Effect, EffectApp, Result}

object ForComprehension extends EffectApp {
  override def mainEffect(args: Array[String]): Effect[Any] =
    for {
      int      <- Effect(42)
      string   <- Effect("hello")
      combined <- Effect(string + int)
      asyncResult <- Effect.callback { complete =>
        println("Async started")
        Thread.sleep(100)
        println(s"Done: $combined")
        complete(Result.Value(1))
      }
      result <- Effect(s"Result is $asyncResult")
    } yield {
      result
    }
}
