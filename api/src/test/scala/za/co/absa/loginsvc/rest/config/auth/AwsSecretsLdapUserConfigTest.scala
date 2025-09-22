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

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginsvc.rest.model.AwsSecret
import za.co.absa.loginsvc.utils.AwsSecretsUtils

import java.time.Instant

class AwsSecretsLdapUserConfigTest extends AnyFlatSpec with Matchers {

  class AwsSecretsUtilsTestSuccess extends AwsSecretsUtils {
    override def fetchSecret(secretName: String,
                             region: String,
                             secretFields: Array[String],
                             versionStage: Option[String] = None): Option[AwsSecret] =
    {
      Some(AwsSecret(Map(
            "accountName" -> "ldap-user123",
            "accountPwd" -> "password456"),
          Instant.now()))
    }
  }

  class AwsSecretsUtilsTestFail extends AwsSecretsUtils {
    override def fetchSecret(secretName: String,
                             region: String,
                             secretFields: Array[String],
                             versionStage: Option[String] = None): Option[AwsSecret] = None
  }

  class AwsSecretsLdapUserConfigTestClass(secretName: String,
                                          region: String,
                                          usernameFieldName: String,
                                          passwordFieldName: String,
                                          testClass: AwsSecretsUtils)
    extends AwsSecretsLdapUserConfig(
      secretName,
      region,
      usernameFieldName,
      passwordFieldName) {
    override private[auth] def AwsUtils: AwsSecretsUtils = testClass
  }

  "AwsSecretsLdapUserConfig" should "return a username and password when AwsSecretsUtils returns a value" in {
    val awsSecretsUtilsTestSuccess = new AwsSecretsUtilsTestSuccess
    val awsSecretsLdapUserConfigTestClass = new AwsSecretsLdapUserConfigTestClass(
      "secret",
      "region",
      "accountName",
      "accountPwd",
      awsSecretsUtilsTestSuccess)

    awsSecretsLdapUserConfigTestClass.username shouldBe "ldap-user123"
    awsSecretsLdapUserConfigTestClass.password shouldBe "password456"
  }

  "AwsSecretsLdapUserConfig" should "Throw and exception if no values are returned by AwsSecretsUtils" in {
    val awsSecretsUtilsTestFail = new AwsSecretsUtilsTestFail
    val exception = intercept[Exception]
    {
      new AwsSecretsLdapUserConfigTestClass(
        "secret",
        "region",
        "accountName",
        "accountPwd",
        awsSecretsUtilsTestFail)
    }

    exception.getMessage shouldBe "Error retrieving username and password from from AWS Secrets Manager"
  }

}
