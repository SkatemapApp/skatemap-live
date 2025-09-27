package core.json

import scala.util.Try

object JsonFieldExtractor {
  def extractString(json: String, field: String): Either[String, String] = {
    val pattern = s""""$field"\\s*:\\s*"([^"]+)"""".r
    pattern
      .findFirstMatchIn(json)
      .map(_.group(1))
      .toRight(s"Missing $field")
  }

  def extractDouble(json: String, field: String): Either[String, Double] = {
    val pattern = s""""$field"\\s*:\\s*([^,}\\s]+)""".r
    pattern
      .findFirstMatchIn(json)
      .map(_.group(1))
      .toRight(s"Missing $field")
      .flatMap(str => Try(str.toDouble).toEither.left.map(_ => s"Invalid $field"))
  }

  def extractLong(json: String, field: String): Either[String, Long] = {
    val pattern = s""""$field"\\s*:\\s*([^,}\\s]+)""".r
    pattern
      .findFirstMatchIn(json)
      .map(_.group(1))
      .toRight(s"Missing $field")
      .flatMap(str => Try(str.toLong).toEither.left.map(_ => s"Invalid $field"))
  }

  def extractOptionalLong(json: String, field: String, default: Long): Either[String, Long] = {
    val pattern = s""""$field"\\s*:\\s*([^,}\\s]+)""".r
    pattern.findFirstMatchIn(json) match {
      case Some(m) => Try(m.group(1).toLong).toEither.left.map(_ => s"Invalid $field")
      case None    => Right(default)
    }
  }
}
