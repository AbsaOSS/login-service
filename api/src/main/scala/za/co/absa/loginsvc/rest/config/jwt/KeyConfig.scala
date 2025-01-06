/*
 * Copyright 2023 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.absa.loginsvc.rest.config.jwt

import io.jsonwebtoken.SignatureAlgorithm
import org.slf4j.LoggerFactory
import za.co.absa.loginsvc.rest.config.validation.{ConfigValidatable, ConfigValidationException, ConfigValidationResult}
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}

import java.security.KeyPair
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

trait KeyConfig extends ConfigValidatable {
  def algName: String
  def accessExpTime: FiniteDuration
  def refreshExpTime: FiniteDuration
  def keyRotationTime: Option[FiniteDuration]
  def keyPair(): (KeyPair, Option[KeyPair])
  def throwErrors(): Unit

  final def jwtAlgorithmToCryptoAlgorithm : String = {

    val algorithmMap: Map[String, String] = Map(
      "RS" -> "RSA",
      "ES" -> "ECDSA"
    )

    algorithmMap.getOrElse(algName.take(2), {
      throw new IllegalArgumentException(s"Unsupported JWT algorithm: $algName")
    })
  }

  private val logger = LoggerFactory.getLogger(classOf[KeyConfig])

  override def validate(): ConfigValidationResult = {

    val algValidation = Try {
      SignatureAlgorithm.valueOf(algName)
    } match {
      case Success(_) => ConfigValidationSuccess
      case Failure(e: IllegalArgumentException) if e.getMessage.contains("No enum constant") =>
        ConfigValidationError(ConfigValidationException(s"Invalid algName '$algName' was given."))
      case Failure(e) => throw e
    }

    val accessExpTimeResult = if (accessExpTime < KeyConfig.minAccessExpTime) {
      ConfigValidationError(ConfigValidationException(s"accessExpTime must be at least ${KeyConfig.minAccessExpTime}"))
    } else ConfigValidationSuccess

    val refreshExpTimeResult = if (refreshExpTime < KeyConfig.minRefreshExpTime) {
      ConfigValidationError(ConfigValidationException(s"refreshExpTime must be at least ${KeyConfig.minRefreshExpTime}"))
    } else ConfigValidationSuccess

    val keyRotationTimeResult = if (keyRotationTime.nonEmpty && keyRotationTime.get < KeyConfig.minKeyRotationTime) {
      ConfigValidationError(ConfigValidationException(s"keyRotationTime must be at least ${KeyConfig.minKeyRotationTime}"))
    } else ConfigValidationSuccess

    if (keyRotationTime.isEmpty) {
      logger.warn("keyRotationTime is not set in config, key-pair will not be rotated!")
    }

    algValidation.merge(accessExpTimeResult).merge(refreshExpTimeResult).merge(keyRotationTimeResult)
  }
}

object KeyConfig {
  val minAccessExpTime: FiniteDuration = FiniteDuration(10, TimeUnit.MILLISECONDS)
  val minRefreshExpTime: FiniteDuration = FiniteDuration(10, TimeUnit.MILLISECONDS)
  val minKeyRotationTime: FiniteDuration = FiniteDuration(10, TimeUnit.MILLISECONDS)
}
