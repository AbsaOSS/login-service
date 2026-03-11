/*
 * Copyright 2026 ABSA Group Limited
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

import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}
import za.co.absa.loginsvc.rest.config.validation.{ConfigValidatable, ConfigValidationException, ConfigValidationResult}

/**
 * Configuration for MS Entra (Azure AD) Bearer token authentication provider.
 *
 * @param tenantId   Azure AD tenant ID (directory ID)
 * @param clientId   Application (client) ID registered in Entra
 * @param audience   Expected value of the JWT 'aud' claim, e.g. "api://your-client-id"
 * @param order      Provider ordering (0 = disabled, 1+ = active)
 * @param attributes Optional mapping from Entra JWT claim names to LS JWT claim names
 */
case class MsEntraConfig(
  tenantId: String,
  clientId: String,
  audience: String,
  order: Int,
  attributes: Option[Map[String, String]]
) extends ConfigValidatable with ConfigOrdering {

  def throwErrors(): Unit =
    this.validate().throwOnErrors()

  override def validate(): ConfigValidationResult = {
    if (order > 0) {
      val results = Seq(
        Option(tenantId)
          .map(_ => ConfigValidationSuccess)
          .getOrElse(ConfigValidationError(ConfigValidationException("tenantId is empty"))),

        Option(clientId)
          .map(_ => ConfigValidationSuccess)
          .getOrElse(ConfigValidationError(ConfigValidationException("clientId is empty"))),

        Option(audience)
          .map(_ => ConfigValidationSuccess)
          .getOrElse(ConfigValidationError(ConfigValidationException("audience is empty")))
      )

      results.foldLeft[ConfigValidationResult](ConfigValidationSuccess)(ConfigValidationResult.merge)
    } else ConfigValidationSuccess
  }
}
