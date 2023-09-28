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
import za.co.absa.loginsvc.rest.config.validation.{ConfigValidatable, ConfigValidationException, ConfigValidationResult}
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}
import scala.util.{Failure, Success, Try}

case class JwtConfig(
                      generateInMemory: Option[GenerateKeysConfig],
                      fetchFromAws: Option[FetchSecretConfig])
  extends ConfigValidatable {

  def throwErrors(): Unit =
    this.validate().throwOnErrors()

  override def validate(): ConfigValidationResult = {

    if(generateInMemory.isEmpty && fetchFromAws.isEmpty)
      {
        ConfigValidationError(
          ConfigValidationException(
            "generateInMemory and fetchFromAws configs are missing. Please ensure that at least one is provided."))
      }
    else
      ConfigValidationSuccess
  }
}
case class GenerateKeysConfig(
                               algName: String,
                               expTime: Int)
  extends ConfigValidatable {

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

    val expTimeResult = if (expTime < 1) {
      ConfigValidationError(ConfigValidationException("expTime must be positive (hours)"))
    } else ConfigValidationSuccess

    algValidation.merge(expTimeResult)
  }
}

case class FetchSecretConfig(
                              secretName: String,
                              region: String,
                              privateAwsKey: String,
                              publicAwsKey: String,
                              expAwsKey: String,
                              algAwsKey: String,
                              refreshTime: Int
                             )
  extends ConfigValidatable {
  def throwErrors(): Unit =
    this.validate().throwOnErrors()

  override def validate(): ConfigValidationResult = {

    val results = Seq(
      Option(secretName)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("secretName is empty"))),

      Option(region)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("region is empty"))),

      Option(privateAwsKey)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("privateAwsKey is empty"))),

        Option (publicAwsKey)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("publicAwsKey is empty"))),

      Option (expAwsKey)
          .map(_ => ConfigValidationSuccess)
          .getOrElse(ConfigValidationError(ConfigValidationException("expAwsKey is empty"))),

      Option(algAwsKey)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("algAwsKey is empty"))),

      Option (refreshTime)
          .map(_ => ConfigValidationSuccess)
          .getOrElse(ConfigValidationError(ConfigValidationException("refreshTime is empty")))
    )

    val resultsMerge = results.foldLeft[ConfigValidationResult](ConfigValidationSuccess)(ConfigValidationResult.merge)

    val TimeResult = if (refreshTime < 1) {
      ConfigValidationError(ConfigValidationException("refreshTime must be positive (hours)"))
    } else ConfigValidationSuccess

    resultsMerge.merge(TimeResult)
  }
}