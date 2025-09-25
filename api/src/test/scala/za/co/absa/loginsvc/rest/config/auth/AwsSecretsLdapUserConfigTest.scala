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

import org.mockito.Mockito.{mock, when}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginsvc.rest.model.AwsSecret
import za.co.absa.loginsvc.utils.AwsSecretsUtils

import java.time.Instant

class AwsSecretsLdapUserConfigTest extends AnyFlatSpec with Matchers {

  "AwsSecretsLdapUserConfig" should "return a username and password when AwsSecretsUtils returns a value" in {
    val awsSecretsUtilsMock = mock(classOf[AwsSecretsUtils])
    when(awsSecretsUtilsMock.fetchSecret(
      "secret",
      "region",
      Array("accountName", "accountPwd")))
      .thenAnswer(_ =>
        Some(AwsSecret(Map(
          "accountName" -> "ldap-user123",
          "accountPwd" -> "password456"),
          Instant.now())))
    val awsSecretsLdapUserConfigTest = new AwsSecretsLdapUserConfig(
      "secret",
      "region",
      "accountName",
      "accountPwd") {
      override private[auth] def AwsUtils: AwsSecretsUtils = awsSecretsUtilsMock
    }
    awsSecretsLdapUserConfigTest.username shouldBe "ldap-user123"
    awsSecretsLdapUserConfigTest.password shouldBe "password456"
  }

  "AwsSecretsLdapUserConfig" should "Throw and exception if no values are returned by AwsSecretsUtils" in {
    val awsSecretsUtilsMock = mock(classOf[AwsSecretsUtils])
    when(awsSecretsUtilsMock.fetchSecret(
      "secret",
      "region",
      Array("accountName", "accountPwd")))
      .thenAnswer(_ => None)
    val exception = intercept[Exception]
    {
      new AwsSecretsLdapUserConfig(
        "secret",
        "region",
        "accountName",
        "accountPwd") {
        override private[auth] def AwsUtils: AwsSecretsUtils = awsSecretsUtilsMock
      }
    }
    exception.getMessage shouldBe "Error retrieving username and password from from AWS Secrets Manager"
  }
}
