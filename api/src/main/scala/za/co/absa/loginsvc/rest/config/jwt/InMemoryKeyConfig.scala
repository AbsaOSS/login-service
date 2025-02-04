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
import org.slf4j.LoggerFactory
import za.co.absa.loginsvc.rest.config.validation.{ConfigValidationException, ConfigValidationResult}
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}

import java.security.KeyPair
import scala.concurrent.duration.FiniteDuration

case class InMemoryKeyConfig(
  algName: String,
  accessExpTime: FiniteDuration,
  refreshExpTime: FiniteDuration,
  keyRotationTime: Option[FiniteDuration],
  keyPhaseOutTime: Option[FiniteDuration],
  keyLayOverTime: Option[FiniteDuration]
) extends KeyConfig {

  private var oldKeyPair: Option[KeyPair] = None
  private val logger = LoggerFactory.getLogger(classOf[InMemoryKeyConfig])

  override def keyPair(): (KeyPair, Option[KeyPair]) = {
    logger.info(s"Generating new keys - every ${keyRotationTime.getOrElse("?")}")
    val newKeyPair = Keys.keyPairFor(SignatureAlgorithm.valueOf(algName))
    val result = (newKeyPair, oldKeyPair)
    oldKeyPair = Some(newKeyPair)
    result
  }

  override def validate(): ConfigValidationResult = {
    val keyPhaseOutTimeResult = if(keyPhaseOutTime.nonEmpty && keyRotationTime.nonEmpty
      && keyPhaseOutTime.get > keyRotationTime.get) {
      ConfigValidationError(ConfigValidationException(s"keyPhaseOutTime must be lower than keyRotationTime!"))
    } else ConfigValidationSuccess

    val keyLayOverTimeResult = if(keyLayOverTime.nonEmpty && keyRotationTime.nonEmpty
      && keyLayOverTime.get > keyRotationTime.get) {
      ConfigValidationError(ConfigValidationException(s"keyLayOverTime must be lower than keyRotationTime!"))
    } else ConfigValidationSuccess

    super.validate()
      .merge(keyPhaseOutTimeResult)
      .merge(keyLayOverTimeResult)
  }

  override def throwErrors(): Unit = this.validate().throwOnErrors()
}

