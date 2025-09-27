package skatemap.core

import java.util.UUID
import scala.util.{Failure, Success, Try}

object UuidValidator {
  def validateEventId(value: String): Either[ValidationError, UUID] =
    Try(UUID.fromString(value)) match {
      case Success(uuid) => Right(uuid)
      case Failure(_)    => Left(InvalidSkatingEventIdError())
    }

  def validateSkaterId(value: String): Either[ValidationError, UUID] =
    Try(UUID.fromString(value)) match {
      case Success(uuid) => Right(uuid)
      case Failure(_)    => Left(InvalidSkaterIdError())
    }
}
