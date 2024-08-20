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

package za.co.absa.loginsvc.rest.config.auth

import za.co.absa.loginsvc.rest.config.validation.{ConfigValidatable, ConfigValidationException, ConfigValidationResult}
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}

case class KerberosConfig(
  krbFileLocation: String,
  keytabFileLocation: String,
  spn: String,
  debug: Option[Boolean]) extends ConfigValidatable {

  override def validate(): ConfigValidationResult = {
    val results = Seq(
      Option(krbFileLocation)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("krbFileLocation is empty"))),
      Option(keytabFileLocation)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("keytabFileLocation is empty"))),
      Option(spn)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("spn is empty")))
    )
    results.foldLeft[ConfigValidationResult](ConfigValidationSuccess)(ConfigValidationResult.merge)
  }
}
