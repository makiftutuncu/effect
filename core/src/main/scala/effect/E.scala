package effect

import java.time.Instant

import scala.collection.mutable

final case class E(
  message: String,
  code: Option[Int] = None,
  kind: Option[String] = None,
  timestamp: Option[Instant] = None,
  causes: List[E] = List.empty,
  data: Map[String, String] = Map.empty
) {
  val hasCode: Boolean      = code.isDefined
  val hasKind: Boolean      = kind.isDefined
  val hasTimestamp: Boolean = timestamp.isDefined
  val hasCauses: Boolean    = causes.nonEmpty
  val hasData: Boolean      = data.nonEmpty

  def withCode(newCode: Int): E                 = copy(code = Some(newCode))
  def withKind(newKind: String): E              = copy(kind = Some(newKind))
  def withTimestamp(newTimestamp: Instant): E   = copy(timestamp = Some(newTimestamp))
  def withTimestamp: E                          = withTimestamp(Instant.now)
  def withCauses(newCauses: List[E]): E         = copy(causes = newCauses)
  def withData(newData: Map[String, String]): E = copy(data = newData)

  def addCauses(firstCause: E, otherCauses: E*): E = copy(causes = causes ++ (firstCause +: otherCauses.toList))

  def addData[K, V](firstPair: (K, V), otherPairs: (K, V)*): E = {
    val pairs = ((firstPair._1.toString -> firstPair._2.toString) +: otherPairs.map { case (k, v) => k.toString -> v.toString }).toMap
    copy(data = data ++ pairs)
  }

  def toException: E.AsException = E.AsException(this)

  override def toString: String = {
    def escape(s: String): String = s.replace("\"", "\\\"")

    val builder = mutable.StringBuilder("{")
    builder.append(s""""message":"${escape(message)}"""")
    code.foreach(c => builder.append(s""","code":$c"""))
    kind.foreach(k => builder.append(s""","kind":"${escape(k)}""""))
    timestamp.foreach(t => builder.append(s""","timestamp":"$t""""))
    if (hasCauses) {
      val causesString = causes.mkString("[", ",", "]")
      builder.append(s""","causes":$causesString""")
    }
    if (hasData) {
      val dataString = data.map { case (k, v) => s""""${escape(k)}":"${escape(v)}"""" }.mkString("{", ",", "}")
      builder.append(s""","data":$dataString""")
    }
    builder.append("}").toString()
  }
}

object E {
  final case class AsException(e: E) extends Exception(e.toString, null, true, false)

  def fromThrowable(t: Throwable): E =
    t match {
      case eae: E.AsException =>
        eae.e

      case _ =>
        val cause            = Option(t.getCause).map(fromThrowable)
        val suppressedCauses = Option(t.getSuppressed).map(_.map(fromThrowable))

        E(t.getMessage).withCauses(cause.toList ++ suppressedCauses.fold(List.empty)(_.toList))
    }
}
