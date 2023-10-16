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
import io.jsonwebtoken.security.Keys
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}
import za.co.absa.loginsvc.rest.config.validation.{ConfigValidationException, ConfigValidationResult}

import scala.util.{Failure, Success, Try}
import java.security.KeyPair
import scala.concurrent.duration.FiniteDuration

case class InMemoryKeyConfig (algName: String,
                              accessExpTime: FiniteDuration,
                              rotationTime: FiniteDuration)
  extends KeyConfig {

  override def refreshKeyTime : FiniteDuration = rotationTime
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

    val rotationTimeResult = if (refreshKeyTime < minRefreshKeyTime) {
      ConfigValidationError(ConfigValidationException(s"rotationTime must be at least $minRefreshKeyTime"))
    } else ConfigValidationSuccess

    algValidation.merge(accessExpTimeResult).merge(rotationTimeResult)
  }
}
