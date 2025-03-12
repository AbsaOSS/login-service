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
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}
import za.co.absa.loginsvc.rest.config.validation.{ConfigValidatable, ConfigValidationException, ConfigValidationResult}
import za.co.absa.loginsvc.utils.AwsSecretsUtils


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
                                     enableKerberos: Option[KerberosConfig],
                                     LdapRetry: Option[LdapRetryConfig],
                                     attributes: Option[Map[String, String]])
  extends ConfigValidatable with ConfigOrdering
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

      val requiredResults = results.foldLeft[ConfigValidationResult](ConfigValidationSuccess)(ConfigValidationResult.merge)
      val kerberosResults = enableKerberos match {
        case Some(x) => x.validate()
        case None => ConfigValidationSuccess
      }
      val retryResults = LdapRetry match {
        case Some(x) => x.validate()
        case None => ConfigValidationSuccess
      }
      requiredResults.merge(kerberosResults).merge(retryResults)
    }
    else ConfigValidationSuccess
  }
}

case class LdapRetryConfig(attempts: Int, delayMs: Int) extends ConfigValidatable {
  override def validate(): ConfigValidationResult = {
    val results = Seq(
      Option(attempts)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("attempts is empty"))),
      Option(delayMs)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("delayMs is empty")))
    )
    results.foldLeft[ConfigValidationResult](ConfigValidationSuccess)(ConfigValidationResult.merge)
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
