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
                                private val inConfigAccount: Option[IntegratedLdapUserConfig],
                                private val awsSecretsManagerAccount: Option[AwsSecretsLdapUserConfig])
{
  private val ldapUserDetails: LdapUser = (inConfigAccount, awsSecretsManagerAccount) match {
    case (Some(_), Some(_)) =>
      throw ConfigValidationException("Both integratedLdapUserConfig and awsSecretsLdapUserConfig exist. Please choose only one.")

    case (None, None) =>
      throw ConfigValidationException("Neither integratedLdapUserConfig nor awsSecretsLdapUserConfig exists. One of them should exist.")

    case _ =>
      val ldapConfig: LdapUser = inConfigAccount.orElse(awsSecretsManagerAccount).get
      ldapConfig.throwErrors()

      ldapConfig
  }

  val username: String = String.format(accountPattern, ldapUserDetails.username)
  val password: String = ldapUserDetails.password
}

case class IntegratedLdapUserConfig(username: String, password: String) extends LdapUser
{
  def throwErrors(): Unit = this.validate().throwOnErrors()
}

case class AwsSecretsLdapUserConfig(private val secretName: String,
                                    private val region: String,
                                    private val usernameFieldName: String,
                                    private val passwordFieldName: String) extends LdapUser {

  def username: String = ""
  def password: String = ""
  def throwErrors(): Unit = this.validate().throwOnErrors()
}

trait LdapUser extends ConfigValidatable {
  def username: String
  def password: String

  def throwErrors(): Unit

  override def validate(): ConfigValidationResult = {
    ConfigValidationSuccess
  }
}
