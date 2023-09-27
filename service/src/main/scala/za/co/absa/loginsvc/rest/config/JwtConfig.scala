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

package za.co.absa.loginsvc.rest.config

import io.jsonwebtoken.SignatureAlgorithm
import za.co.absa.loginsvc.rest.config.JwtConfig.{minAccessExpTime, minRefreshExpTime}
import za.co.absa.loginsvc.rest.config.validation.{ConfigValidatable, ConfigValidationException, ConfigValidationResult}
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

object JwtConfig {
  val minAccessExpTime: FiniteDuration = FiniteDuration(5, TimeUnit.SECONDS)
  val minRefreshExpTime: FiniteDuration = FiniteDuration(10, TimeUnit.SECONDS)
}

case class JwtConfig(
  algName: String,
  accessExpTime: FiniteDuration,
  refreshExpTime: FiniteDuration
) extends ConfigValidatable {

  def throwErrors(): Unit =
    this.validate().throwOnErrors()

  /** May throw ConfigValidationException or IllegalArgumentException */
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
