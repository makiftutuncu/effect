package dev.akif.effect.examples

import effect.{Effect, EffectApp}

import e.scala.E

object Fail extends EffectApp {
  override def mainEffect(args: Array[String]): Effect[Any] =
    Effect.error(E.name("error"))
}
