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

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.{GetSecretValueRequest, GetSecretValueResponse}
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}
import za.co.absa.loginsvc.rest.config.validation.{ConfigValidatable, ConfigValidationException, ConfigValidationResult}


/**
 * Configuration for AD LDAP(s) authentication provider.
 *
 * @param domain AD domain name, ex. "some.domain.com"
 * @param url URL to AD LDAP, ex. "ldaps://some.domain.com:636/"
 * @param searchFilter LDAP filter used when searching for groups, ex. "(samaccountname={1})"
 */
case class ActiveDirectoryLDAPConfig(domain: String,
                                     url: String,
                                     searchFilter: String,
                                     order: Int,
                                     serviceAccount: ServiceAccountConfig,
                                     attributes: Option[Map[String, String]])
  extends ConfigValidatable with DynamicAuthOrder
{

  def throwErrors(): Unit =
    this.validate().throwOnErrors()

  override def validate(): ConfigValidationResult = {

    if(order > 0)
    {
      val results = Seq(
        Option(domain)
          .map(_ => ConfigValidationSuccess)
          .getOrElse(ConfigValidationError(ConfigValidationException("domain is empty"))),

        Option(url)
          .map(_ => ConfigValidationSuccess)
          .getOrElse(ConfigValidationError(ConfigValidationException("url is empty"))),

        Option(serviceAccount)
          .map(_ => ConfigValidationSuccess)
          .getOrElse(ConfigValidationError(ConfigValidationException("serviceAccount is empty"))),

        Option(searchFilter)
          .map(_ => ConfigValidationSuccess)
          .getOrElse(ConfigValidationError(ConfigValidationException("searchFilter is empty")))
      )

      results.foldLeft[ConfigValidationResult](ConfigValidationSuccess)(ConfigValidationResult.merge)
    }
    else ConfigValidationSuccess
  }
}

case class ServiceAccountConfig(private val accountPattern: String,
                                private val inConfigAccount: Option[LdapUserCredentialsConfig],
                                private val awsSecretsManagerAccount: Option[AwsSecretsLdapUserConfig])
{
  private val ldapUserDetails: LdapUser = (inConfigAccount, awsSecretsManagerAccount) match {
    case (Some(_), Some(_)) =>
      throw ConfigValidationException("Both integratedLdapUserConfig and awsSecretsLdapUserConfig exist. Please choose only one.")

    case (None, None) =>
      throw ConfigValidationException("Neither integratedLdapUserConfig nor awsSecretsLdapUserConfig exists. Exactly one of them should be present.")

    case (inConfig@Some(_), None) =>
      val ldapConfig: LdapUser = inConfig.get
      ldapConfig.throwOnErrors()

      ldapConfig

    case (None, awsConfig@Some(_)) =>
      val ldapConfig: LdapUser = awsConfig.get
      ldapConfig.throwOnErrors()

      ldapConfig

    case _ =>
      throw ConfigValidationException("Error with current config concerning integratedLdapUserConfig or awsSecretsLdapUserConfig")
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
                                    private val passwordFieldName: String) extends LdapUser {

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

    val default = DefaultCredentialsProvider.create

    val client = SecretsManagerClient.builder
      .region(Region.of(region))
      .credentialsProvider(default)
      .build

    val getSecretValueRequest = GetSecretValueRequest.builder.secretId(secretName).build

    try {
      logger.info("Attempting to fetch account data from AWS Secrets Manager")
      val getSecretValueResponse: GetSecretValueResponse = client.getSecretValue(getSecretValueRequest)
      val secret = getSecretValueResponse.secretString
      logger.info("account data retrieved.")
      val rootNode: JsonNode = new ObjectMapper().readTree(secret)

      (rootNode.get(usernameFieldName).asText(), rootNode.get(passwordFieldName).asText())
    }
    catch {
      case e: Throwable =>
        logger.error(s"Error occurred retrieving account data from AWS Secrets Manager", e)
        throw e
    }
  }
}

trait LdapUser extends ConfigValidatable {
  def username: String
  def password: String
  def throwOnErrors(): Unit

  override def validate(): ConfigValidationResult = {
    val results = Seq(
      Option(username)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("username is empty"))),
      Option(password)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("password is empty")))
    )

    results.foldLeft[ConfigValidationResult](ConfigValidationSuccess)(ConfigValidationResult.merge)
  }
}
