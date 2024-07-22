package za.co.absa.loginsvc.rest.config.auth

import za.co.absa.loginsvc.rest.config.validation.{ConfigValidatable, ConfigValidationException, ConfigValidationResult}
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}

case class KerberosConfig(krbFileLocation: String, keytabFileLocation: String, debug: Option[Boolean]) extends ConfigValidatable {

  override def validate(): ConfigValidationResult = {
    val results = Seq(
      Option(krbFileLocation)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("krbFileLocation is empty"))),
      Option(keytabFileLocation)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("keytabFileLocation is empty")))
    )
    results.foldLeft[ConfigValidationResult](ConfigValidationSuccess)(ConfigValidationResult.merge)
  }
}
