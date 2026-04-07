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

import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}
import za.co.absa.loginsvc.rest.config.validation.{ConfigValidatable, ConfigValidationException, ConfigValidationResult}

/**
 * Configuration for MS Entra (Azure AD) Bearer token authentication provider.
 *
 * @param tenantId      Azure AD tenant ID (directory ID)
 * @param clientId      Application (client) ID registered in Entra
 * @param clientSecret  Client secret used to acquire a Graph API token for username resolution.
 *                      When set, the token's `preferred_username` (UPN) is exchanged for
 *                      `onPremisesSamAccountName` via MS Graph, and the resulting username
 *                      is formatted as lower-case `samAccountName`.
 * @param audiences     Accepted JWT 'aud' claim values — tokens from any listed app are accepted;
 *                      empty list accepts any token from the tenant
 * @param domains       Mapping from on-premises DNS domain names to their NetBIOS short names,
 *                      e.g. `corp.example.com -> CORP`. Required when `clientSecret` is set
 *                      so known domains can be allowed and their mapped AB values logged.
 * @param order         Provider ordering (0 = disabled, 1+ = active)
 * @param attributes    Optional mapping from Entra JWT claim names to LS JWT claim names
 * @param loginBaseUrl  Base URL for Microsoft login/token endpoints.
 *                      Defaults to the public Azure cloud (`https://login.microsoftonline.com`).
 *                      Override for sovereign clouds (e.g. Azure Government).
 * @param graphBaseUrl  Base URL for the Microsoft Graph API.
 *                      Defaults to the public Azure cloud (`https://graph.microsoft.com`).
 *                      Override for sovereign clouds (e.g. Azure Government).
 */
case class MsEntraConfig(
  tenantId: String,
  clientId: String,
  clientSecret: Option[String] = None,
  audiences: List[String],
  domains: Option[Map[String, String]] = None,
  order: Int,
  attributes: Option[Map[String, String]],
  loginBaseUrl: String = "https://login.microsoftonline.com",
  graphBaseUrl: String = "https://graph.microsoft.com"
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
          .getOrElse(ConfigValidationError(ConfigValidationException("clientId is empty")))
      )

      results.foldLeft[ConfigValidationResult](ConfigValidationSuccess)(ConfigValidationResult.merge)
    } else ConfigValidationSuccess
  }
}
