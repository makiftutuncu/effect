package dev.akif.effect.examples

import effect.{Effect, EffectApp}

import scala.io.StdIn

object Greeting extends EffectApp:
  override val traceEnabled: Boolean = false

  override def mainEffect(args: Array[String]): Effect[Any] =
    for
      _    <- Effect(print("Please enter your name: "))
      name <- Effect(StdIn.readLine())
      _    <- Effect(println(s"Hello, $name!"))
    yield ()
