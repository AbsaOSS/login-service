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

import org.slf4j.LoggerFactory
import za.co.absa.loginsvc.rest.config.validation.{ConfigValidationException, ConfigValidationResult}
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}
import za.co.absa.loginsvc.utils.AwsSecretsUtils

case class ServiceAccountConfig(private val accountPattern: String,
                                private val inConfigAccount: Option[LdapUserCredentialsConfig],
                                private val awsSecretsManagerAccount: Option[AwsSecretsLdapUserConfig])
{
  private val ldapUserDetails: LdapUser = (inConfigAccount, awsSecretsManagerAccount) match {
    case (Some(_), Some(_)) =>
      throw ConfigValidationException("Both inConfigAccount and awsSecretsLdapUserConfig exist. Please choose only one.")

    case (None, None) =>
      throw ConfigValidationException("Neither integratedLdapUserConfig nor awsSecretsLdapUserConfig exists. Exactly one of them should be present.")

    case (Some(inConfig), None) =>
      inConfig.throwOnErrors()
      inConfig

    case (None, Some(awsConfig)) =>
      awsConfig.throwOnErrors()
      awsConfig

    case _ =>
      throw ConfigValidationException("Error with current config concerning inConfigAccount or awsSecretsLdapUserConfig")
  }

  val username: String = String.format(accountPattern, ldapUserDetails.username)
  val password: String = ldapUserDetails.password
}

case class LdapUserCredentialsConfig (username: String, password: String) extends LdapUser
{
  def throwOnErrors(): Unit = this.validate().throwOnErrors()
}

case class AwsSecretsLdapUserConfig(private val secretName: String,
                                    private val region: String,
                                    private val usernameFieldName: String,
                                    private val passwordFieldName: String) extends LdapUser
{

  private val logger = LoggerFactory.getLogger(classOf[LdapUser])

  val (username, password) = getUsernameAndPasswordFromSecret
  def throwOnErrors(): Unit = this.validate().throwOnErrors()
  override def validate(): ConfigValidationResult = {
    val results = Seq(
      Option(secretName)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("secretName is empty"))),

      Option(region)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("region is empty"))),

      Option(usernameFieldName)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("usernameFieldName is empty"))),

      Option(passwordFieldName)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("passwordFieldName is empty")))
    )

    val awsSecretsResultsMerge = results.foldLeft[ConfigValidationResult](ConfigValidationSuccess)(ConfigValidationResult.merge)
    awsSecretsResultsMerge.merge(super.validate())
  }

  private def getUsernameAndPasswordFromSecret: (String, String) = {
    try {
      val secrets = AwsSecretsUtils.fetchSecret(secretName, region, Array(usernameFieldName, passwordFieldName))
      (secrets(usernameFieldName), secrets(passwordFieldName))
    }
    catch {
      case e: Throwable =>
        logger.error(s"Error occurred retrieving account data from AWS Secrets Manager", e)
        throw e
    }
  }
}
