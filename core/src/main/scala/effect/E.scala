package effect

import java.time.Instant

import scala.collection.mutable
import scala.util.control.NoStackTrace

/** An immutable, general purpose error model
  *
  * @param message
  *   A message describing what went wrong, typically a human-readable message
  * @param code
  *   A numerical code for the error, optional
  * @param kind
  *   A kind (type) for the error, typically a machine-readable string, optional
  * @param timestamp
  *   An timestamp of when the error occurred as a [[java.time.Instant]], optional
  * @param causes
  *   A list of errors that caused this error to occur, if any
  * @param data
  *   Additional data in key-value format that's related to the error, typically additional values to help understanding why the error
  *   occurred
  */
final case class E(
  message: String,
  code: Option[Int] = None,
  kind: Option[String] = None,
  timestamp: Option[Instant] = None,
  causes: List[E] = List.empty,
  data: Map[String, String] = Map.empty
) {

  /** Whether this error has a code defined */
  val hasCode: Boolean = code.isDefined

  /** Whether this error has a kind defined */
  val hasKind: Boolean = kind.isDefined

  /** Whether this error has a timestamp defined */
  val hasTimestamp: Boolean = timestamp.isDefined

  /** Whether this error has any cause defined */
  val hasCauses: Boolean = causes.nonEmpty

  /** Whether this error has any data defined */
  val hasData: Boolean = data.nonEmpty

  /** Gets a new E with given code set
    *
    * @param newCode
    *   A new code to set
    *
    * @return
    *   A new E with given code set
    */
  def withCode(newCode: Int): E = copy(code = Some(newCode))

  /** Gets a new E with given kind set
    *
    * @param newKind
    *   A new kind to set
    *
    * @return
    *   A new E with given kind set
    */
  def withKind(newKind: String): E = copy(kind = Some(newKind))

  /** Gets a new E with given timestamp set
    *
    * @param newTimestamp
    *   A new timestamp to set
    *
    * @return
    *   A new E with given timestamp set
    */
  def withTimestamp(newTimestamp: Instant): E = copy(timestamp = Some(newTimestamp))

  /** Gets a new E with timestamp set to current default time
    *
    * @return
    *   A new E with timestamp set to current default time
    *
    * @see
    *   [[java.time.Instant.now]]
    */
  def withTimestamp: E = withTimestamp(Instant.now)

  /** Gets a new E with given list of causes set, this replaces any existing causes
    *
    * @param newCauses
    *   A new list of causes to set
    *
    * @return
    *   A new E with given list of causes set
    */
  def withCauses(newCauses: List[E]): E = copy(causes = newCauses)

  /** Gets a new E with given data set, this replaces any existing data
    *
    * @param newData
    *   A new data to set
    *
    * @return
    *   A new E with given data set
    */
  def withData(newData: Map[String, String]): E = copy(data = newData)

  /** Gets a new E with given causes added to existing causes
    *
    * @param firstCause
    *   A new cause to add
    * @param otherCauses
    *   Zero or more causes to add
    *
    * @return
    *   A new E with given causes added to existing causes
    */
  def addCauses(firstCause: E, otherCauses: E*): E = copy(causes = causes ++ (firstCause +: otherCauses.toList))

  /** Gets a new E with given data added to existing data
    *
    * @param firstPair
    *   A new key-value pair to add
    * @param otherPairs
    *   Zero or more key-value pairs to add
    *
    * @return
    *   A new E with given data added to existing data
    */
  def addData[K, V](firstPair: (K, V), otherPairs: (K, V)*): E = {
    val (firstKey, firstValue) = firstPair
    val newData = otherPairs.foldLeft(data + (firstKey.toString -> firstValue.toString)) { case (data, (k, v)) =>
      data + (k.toString -> v.toString)
    }

    copy(data = newData)
  }

  /** Gets a [[java.lang.Exception]] representation of this error
    *
    * @return
    *   A [[java.lang.Exception]] representation of this error
    */
  def toException: E.AsException = E.AsException(this)

  /** Gets a json formatted string representation of this error
    *
    * @return
    *   A json formatted string representation of this error
    */
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

  /** A [[java.lang.Exception]] representation of an E
    *
    * @param e
    *   underlying error
    */
  final case class AsException(e: E) extends Exception(e.toString) with NoStackTrace

  /** Builds an E from a [[java.lang.Throwable]]
    *
    * @param t
    *   a [[java.lang.Throwable]] error
    *
    * @return
    *   An E built from a [[java.lang.Throwable]]
    */
  def fromThrowable(t: Throwable): E =
    t match {
      case E.AsException(e) =>
        e

      case _ =>
        val cause            = Option(t.getCause).map(fromThrowable)
        val suppressedCauses = Option(t.getSuppressed).map(_.map(fromThrowable))

        E(t.getMessage).withCauses(cause.toList ++ suppressedCauses.fold(List.empty)(_.toList))
    }
}
