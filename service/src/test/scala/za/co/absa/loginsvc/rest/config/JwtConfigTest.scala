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

package za.co.absa.loginsvc.rest.config

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginsvc.rest.config.provider.JwtConfigProvider
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationException
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}

class JwtConfigTest extends AnyFlatSpec with Matchers {

  val jwtConfig: JwtConfig = JwtConfig(
    Option(GenerateKeysConfig("RS256", 2)),
    Option(FetchSecretConfig("Secret", "region", "private", "public", "exp", "alg", 30))
  )
  val inMemoryConfig: GenerateKeysConfig = jwtConfig.generateInMemory.get
  val awsSecretConfig: FetchSecretConfig = jwtConfig.fetchFromAws.get

  "JwtConfig" should "validate expected content" in {
    jwtConfig.validate() shouldBe ConfigValidationSuccess
  }

  "inMemoryConfig" should "validate expected content" in {
    inMemoryConfig.validate() shouldBe ConfigValidationSuccess
  }

  "awsSecretConfig" should "validate expected content" in {
    awsSecretConfig.validate() shouldBe ConfigValidationSuccess
  }

  it should "fail on invalid algorithm" in {
    inMemoryConfig.copy(algName = "ABC").validate() shouldBe
      ConfigValidationError(ConfigValidationException("Invalid algName 'ABC' was given."))
  }

  "inMemoryConfig" should "fail on non-negative expTime" in {
    inMemoryConfig.copy(expTime = -7).validate() shouldBe
      ConfigValidationError(ConfigValidationException("expTime must be positive (hours)"))

  }

  "awsSecretConfig" should "fail on missing value" in {
    awsSecretConfig.copy(secretName = null).validate() shouldBe
      ConfigValidationError(ConfigValidationException("secretName is empty"))
  }

  "awsSecretConfig" should "fail on non-negative refreshTime" in {
    awsSecretConfig.copy(refreshTime = -7).validate() shouldBe
      ConfigValidationError(ConfigValidationException("refreshTime must be positive (minutes)"))
  }
}
