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
import za.co.absa.loginsvc.rest.config.jwt.{AwsSecretsManagerKeyConfig, InMemoryKeyConfig}
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationException
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class JwtConfigTest extends AnyFlatSpec with Matchers {

  val inMemoryKeyConfig: InMemoryKeyConfig = InMemoryKeyConfig("RS256",
    FiniteDuration(15, TimeUnit.MINUTES),
    Option(FiniteDuration(30, TimeUnit.MINUTES)))

  val awsSecretsManagerKeyConfig: AwsSecretsManagerKeyConfig = AwsSecretsManagerKeyConfig("Secret",
    "region",
    "private",
    "public",
    "RS256",
    FiniteDuration(15, TimeUnit.MINUTES),
    Option(FiniteDuration(30, TimeUnit.MINUTES)))

  "inMemoryKeyConfig" should "validate expected content" in {
    inMemoryKeyConfig.validate() shouldBe ConfigValidationSuccess
  }

  "awsSecretsManagerKeyConfig" should "validate expected content" in {
    awsSecretsManagerKeyConfig.validate() shouldBe ConfigValidationSuccess
  }

  "inMemoryKeyConfig" should "fail on invalid algorithm" in {
    inMemoryKeyConfig.copy(algName = "ABC").validate() shouldBe
      ConfigValidationError(ConfigValidationException("Invalid algName 'ABC' was given."))
  }

  "awsSecretsManagerKeyConfig" should "fail on invalid algorithm" in {
    awsSecretsManagerKeyConfig.copy(algName = "ABC").validate() shouldBe
      ConfigValidationError(ConfigValidationException("Invalid algName 'ABC' was given."))
  }

  "inMemoryKeyConfig" should "fail on non-negative accessExpTime" in {
    inMemoryKeyConfig.copy(accessExpTime = FiniteDuration(5, TimeUnit.MILLISECONDS)).validate() shouldBe
      ConfigValidationError(ConfigValidationException(s"accessExpTime must be at least ${inMemoryKeyConfig.minAccessExpTime}"))
  }

  "awsSecretsManagerKeyConfig" should "fail on non-negative accessExpTime" in {
    awsSecretsManagerKeyConfig.copy(accessExpTime = FiniteDuration(5, TimeUnit.MILLISECONDS)).validate() shouldBe
      ConfigValidationError(ConfigValidationException(s"accessExpTime must be at least ${inMemoryKeyConfig.minAccessExpTime}"))
  }

  "inMemoryKeyConfig" should "fail on non-negative refreshExpTime" in {
    inMemoryKeyConfig.copy(rotationTime = Option(FiniteDuration(5, TimeUnit.MILLISECONDS))).validate() shouldBe
      ConfigValidationError(ConfigValidationException(s"rotationTime must be at least ${inMemoryKeyConfig.minRefreshKeyTime}"))
  }

  "awsSecretsManagerKeyConfig" should "fail on non-negative refreshExpTime" in {
    awsSecretsManagerKeyConfig.copy(pollTime = Option(FiniteDuration(5, TimeUnit.MILLISECONDS))).validate() shouldBe
      ConfigValidationError(ConfigValidationException(s"pollTime must be at least ${inMemoryKeyConfig.minRefreshKeyTime}"))
  }

  "awsSecretsManagerKeyConfig" should "fail on missing value" in {
    awsSecretsManagerKeyConfig.copy(secretName = null).validate() shouldBe
      ConfigValidationError(ConfigValidationException("secretName is empty"))
  }
}
