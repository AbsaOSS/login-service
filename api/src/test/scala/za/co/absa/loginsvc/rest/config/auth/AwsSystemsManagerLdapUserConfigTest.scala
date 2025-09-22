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
import za.co.absa.loginsvc.utils.AwsSsmUtils

class AwsSystemsManagerLdapUserConfigTest extends AnyFlatSpec with Matchers {

  class AwsSsmUtilsSuccess extends AwsSsmUtils {
    override def getParameter(paramName: String, decryptIfSecure: Boolean): Option[String] = {
      Some(
        """{
          | "accountName": "ldap-user123",
          | "accountPwd": "password456"
          |}""".stripMargin
      )
    }
  }

  class AwsSsmUtilsFail extends AwsSsmUtils {
    override def getParameter(paramName: String, decryptIfSecure: Boolean): Option[String] = None
  }

  class AwsSystemsManagerLdapUserConfigTestClass(parameter: String,
                                                 decryptIfSecure: Boolean,
                                                 usernameFieldName: String,
                                                 passwordFieldName: String,
                                                 testClass: AwsSsmUtils)
    extends AwsSystemsManagerLdapUserConfig(
      parameter,
      decryptIfSecure,
      usernameFieldName,
      passwordFieldName) {
    override private[auth] def AwsUtils: AwsSsmUtils = testClass
  }

  "AwsSystemsManagerLdapUserConfig" should "return a username and password when AwsSsmUtils returns a value" in {
    val awsSsmUtilsTestSuccess = new AwsSsmUtilsSuccess
    val AwsSystemsManagerLdapUserConfigTestClass = new AwsSystemsManagerLdapUserConfigTestClass(
      "parameter",
      false,
      "accountName",
      "accountPwd",
      awsSsmUtilsTestSuccess)

    AwsSystemsManagerLdapUserConfigTestClass.username shouldBe "ldap-user123"
    AwsSystemsManagerLdapUserConfigTestClass.password shouldBe "password456"
  }

  "AwsSystemsManagerLdapUserConfig" should "Throw and exception if no values are returned by AwsSsmUtils" in {
    val awsSsmUtilsTestFail = new AwsSsmUtilsFail
    val exception = intercept[Exception]
      {
        new AwsSystemsManagerLdapUserConfigTestClass(
          "parameter",
          false,
          "accountName",
          "accountPwd",
          awsSsmUtilsTestFail)
      }

    exception.getMessage shouldBe "Error retrieving username and password from from AWS Systems Manager"
  }
}
