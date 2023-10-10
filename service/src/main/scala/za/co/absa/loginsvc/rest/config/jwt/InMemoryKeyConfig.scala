package za.co.absa.loginsvc.rest.config.jwt

import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}
import za.co.absa.loginsvc.rest.config.validation.{ConfigValidationException, ConfigValidationResult}

import scala.util.{Failure, Success, Try}
import java.security.KeyPair
import scala.concurrent.duration.FiniteDuration

case class InMemoryKeyConfig (algName: String,
                              accessExpTime: FiniteDuration,
                              refreshExpTime: FiniteDuration)
  extends KeyConfig {

  override def keyPair: KeyPair = Keys.keyPairFor(SignatureAlgorithm.valueOf(algName))

  override def throwErrors(): Unit = this.validate().throwOnErrors()

  override def validate(): ConfigValidationResult = {

    val algValidation = Try {
      SignatureAlgorithm.valueOf(algName)
    } match {
      case Success(_) => ConfigValidationSuccess
      case Failure(e: IllegalArgumentException) if e.getMessage.contains("No enum constant") =>
        ConfigValidationError(ConfigValidationException(s"Invalid algName '$algName' was given."))
      case Failure(e) => throw e
    }

    val accessExpTimeResult = if (accessExpTime < minAccessExpTime) {
      ConfigValidationError(ConfigValidationException(s"accessExpTime must be at least $minAccessExpTime"))
    } else ConfigValidationSuccess

    val refreshExpTimeResult = if (refreshExpTime < minRefreshExpTime) {
      ConfigValidationError(ConfigValidationException(s"refreshExpTime must be at least $minRefreshExpTime"))
    } else ConfigValidationSuccess

    algValidation.merge(accessExpTimeResult).merge(refreshExpTimeResult)
  }
}
