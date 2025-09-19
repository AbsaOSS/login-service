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

import org.mockito.Mockito.mock
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationException
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}

class ServiceAccountConfigTest extends AnyFlatSpec with Matchers {

  private val integratedCfg = LdapUserCredentialsConfig("svc-ldap", "password")
  private val cloudCfg = mock(classOf[AwsSecretsLdapUserConfig])
  private val serviceAccountCfg = ServiceAccountConfig(
    "CN=%s,OU=Users,OU=Accounts,DC=domain,DC=subdomain,DC=com",
    Option(integratedCfg),
    None,
    None)

  "ServiceAccountConfig" should "have validated and gotten the correct username and password" in {
    serviceAccountCfg.username shouldBe "CN=svc-ldap,OU=Users,OU=Accounts,DC=domain,DC=subdomain,DC=com"
    serviceAccountCfg.password shouldBe "password"
  }

  "ServiceAccountConfig" should "throw a ConfigValidationException for the incorrect configuration" in {
    val bothNoneException = intercept[ConfigValidationException]
    {
      ServiceAccountConfig(
        "CN=%s,OU=Users,OU=Accounts,DC=domain,DC=subdomain,DC=com",
        None,
        None,
        None)
    }
    bothNoneException.getMessage should be ("None of the options are defined. Exactly one of them should be present.")

    val bothSomeException = intercept[ConfigValidationException]
      {
        ServiceAccountConfig(
          "CN=%s,OU=Users,OU=Accounts,DC=domain,DC=subdomain,DC=com",
          Some(integratedCfg),
          Some(cloudCfg),
          None)
      }

    bothSomeException.getMessage should be ("More than one option is defined. Please choose only one.")
  }

  "LdapUserCredentialsConfig" should "return the correct validation results" in {
    integratedCfg.validate() shouldBe ConfigValidationSuccess

    integratedCfg.copy(username = null).validate() shouldBe
      ConfigValidationError(ConfigValidationException("username is empty"))

    integratedCfg.copy(password = null).validate() shouldBe
      ConfigValidationError(ConfigValidationException("password is empty"))
  }
}
