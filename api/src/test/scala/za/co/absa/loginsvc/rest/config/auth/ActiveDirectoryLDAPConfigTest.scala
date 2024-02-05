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
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationException
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}

class ActiveDirectoryLDAPConfigTest extends AnyFlatSpec with Matchers {

  private val ldapCfg = ActiveDirectoryLDAPConfig("some.domain.com", "ldaps://some.domain.com:636/","SomeAccount", 1, None)

  "ActiveDirectoryLDAPConfig" should "validate expected filled content" in {
    ldapCfg.validate() shouldBe ConfigValidationSuccess
  }

  it should "fail on missing domain/url/search-filter" in {
    ldapCfg.copy(domain = null).validate() shouldBe
      ConfigValidationError(ConfigValidationException("domain is empty"))

    ldapCfg.copy(url = null).validate() shouldBe
      ConfigValidationError(ConfigValidationException("url is empty"))

    ldapCfg.copy(searchFilter = null).validate() shouldBe
      ConfigValidationError(ConfigValidationException("searchFilter is empty"))
  }

  it should "pass validation if disabled despite missing values" in {
    ActiveDirectoryLDAPConfig(null, null, null, 0, None).validate() shouldBe ConfigValidationSuccess
  }
}
