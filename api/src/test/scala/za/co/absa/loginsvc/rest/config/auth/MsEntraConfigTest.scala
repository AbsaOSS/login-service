/*
 * Copyright 2026 ABSA Group Limited
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

class MsEntraConfigTest extends AnyFlatSpec with Matchers {

  private val validConfig = MsEntraConfig(
    tenantId = "test-tenant-id",
    clientId = "test-client-id",
    audience = "api://test-client-id",
    order = 1,
    attributes = Some(Map("preferred_username" -> "upn", "email" -> "email"))
  )

  "MsEntraConfig" should "validate expected filled content" in {
    validConfig.validate() shouldBe ConfigValidationSuccess
  }

  it should "validate with no attributes (they are optional)" in {
    validConfig.copy(attributes = None).validate() shouldBe ConfigValidationSuccess
  }

  it should "validate with empty attributes" in {
    validConfig.copy(attributes = Some(Map.empty)).validate() shouldBe ConfigValidationSuccess
  }

  it should "fail on null tenantId" in {
    val result = validConfig.copy(tenantId = null).validate()
    result shouldBe ConfigValidationError(ConfigValidationException("tenantId is empty"))
  }

  it should "fail on null clientId" in {
    val result = validConfig.copy(clientId = null).validate()
    result shouldBe ConfigValidationError(ConfigValidationException("clientId is empty"))
  }

  it should "fail on null audience" in {
    val result = validConfig.copy(audience = null).validate()
    result shouldBe ConfigValidationError(ConfigValidationException("audience is empty"))
  }

  it should "accumulate multiple validation errors" in {
    val result = validConfig.copy(tenantId = null, clientId = null).validate()
    result shouldBe a[ConfigValidationError]
    result.errors should have size 2
    result.errors.map(_.msg) should contain allOf ("tenantId is empty", "clientId is empty")
  }

  it should "pass validation when disabled (order=0) even with null fields" in {
    MsEntraConfig(tenantId = null, clientId = null, audience = null, order = 0, attributes = None)
      .validate() shouldBe ConfigValidationSuccess
  }

  it should "throw on throwErrors() when invalid" in {
    val exception = intercept[ConfigValidationException] {
      validConfig.copy(tenantId = null).throwErrors()
    }
    exception.msg should include("tenantId is empty")
  }
}
