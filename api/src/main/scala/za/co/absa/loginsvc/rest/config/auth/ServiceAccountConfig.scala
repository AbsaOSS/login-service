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
import com.fasterxml.jackson.databind.ObjectMapper
import za.co.absa.loginsvc.rest.config.validation.{ConfigValidationException, ConfigValidationResult}
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}
import za.co.absa.loginsvc.utils.{AwsSecretsUtils, AwsSsmUtils}

case class ServiceAccountConfig(private val accountPattern: String,
                                private val inConfigAccount: Option[LdapUserCredentialsConfig],
                                private val awsSecretsManagerAccount: Option[AwsSecretsLdapUserConfig],
                                private val awsSystemsManagerAccount: Option[AwsSystemsManagerLdapUserConfig])
{
  private val ldapUserDetails: LdapUser = chooseOne(
    inConfigAccount,
    awsSecretsManagerAccount,
    awsSystemsManagerAccount)

  ldapUserDetails.throwOnErrors()

  val username: String = String.format(accountPattern, ldapUserDetails.username)
  val password: String = ldapUserDetails.password

  private def chooseOne[T](options: Option[T]*): T = {
    options.flatten match {
      case Seq(value) =>
        value
      case Nil =>
        throw ConfigValidationException("None of the options are defined. Exactly one of them should be present.")
      case _ =>
        throw ConfigValidationException("More than one option is defined. Please choose only one.")
    }
  }
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

  private val logger = LoggerFactory.getLogger(classOf[AwsSecretsLdapUserConfig])

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

  //Added for Testing Purposes
  private[auth] def AwsUtils: AwsSecretsUtils = AwsSecretsUtils

  private def getUsernameAndPasswordFromSecret: (String, String) = {
    try {
      val secretsOption = AwsUtils.fetchSecret(secretName, region, Array(usernameFieldName, passwordFieldName))

      secretsOption.fold(
        throw new Exception("Error retrieving username and password from from AWS Secrets Manager")
      ) { secrets =>
        (secrets.secretValue(usernameFieldName), secrets.secretValue(passwordFieldName))
      }

    } catch {
      case e: Throwable =>
        logger.error(s"Error occurred retrieving account data from AWS Secrets Manager", e)
        throw e
    }
  }
}

case class AwsSystemsManagerLdapUserConfig(private val parameter: String,
                                           private val decryptIfSecure: Boolean,
                                           private val usernameFieldName: String,
                                           private val passwordFieldName: String) extends LdapUser
{
  private val logger = LoggerFactory.getLogger(classOf[AwsSystemsManagerLdapUserConfig])

  val (username, password) = getUsernameAndPasswordFromSsm
  def throwOnErrors(): Unit = this.validate().throwOnErrors()
  override def validate(): ConfigValidationResult = {
    val results = Seq(
      Option(parameter)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("parameter is empty"))),

      Option(decryptIfSecure)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("decryptIfSecure is empty"))),

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

  //Added for Testing Purposes
  private[auth] def AwsUtils: AwsSsmUtils = AwsSsmUtils

  private def getUsernameAndPasswordFromSsm: (String, String) = {
    try {
      val responseOption = AwsUtils.getParameter(parameter, decryptIfSecure)
      val objectMapper = new ObjectMapper()

      responseOption.fold(
        throw new Exception("Error retrieving username and password from from AWS Systems Manager")
      ) {
        response => {
          val root = objectMapper.readTree(response)
          val usernameNode = root.get(usernameFieldName)
          val passwordNode = root.get(passwordFieldName)

          if (usernameNode == null || passwordNode == null) {
            throw new Exception(s"Missing '$usernameFieldName' or '$passwordFieldName' fields in SSM JSON")
          }

          val username = usernameNode.asText()
          val password = passwordNode.asText()

          (username, password)
        }
      }
    }
    catch {
      case e: Throwable =>
        logger.error(s"Error occurred retrieving data from AWS Ssm", e)
        throw e
    }
  }
}
