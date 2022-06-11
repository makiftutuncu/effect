package effect

import java.time.Instant

// TODO: Improve API
final case class E(
  message: String,
  code: Option[Int] = None,
  kind: Option[String] = None,
  timestamp: Option[Instant] = None,
  cause: Option[E] = None,
  data: Map[String, String] = Map.empty
)
