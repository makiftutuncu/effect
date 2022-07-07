package effect

trait Tracing { self: TestSuite =>
  override val traceEnabled: Boolean = true
}
