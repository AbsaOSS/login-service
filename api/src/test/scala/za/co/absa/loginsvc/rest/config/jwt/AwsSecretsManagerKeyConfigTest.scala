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

package za.co.absa.loginsvc.rest.config.jwt

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationException
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}

import scala.concurrent.duration._

class AwsSecretsManagerKeyConfigTest extends AnyFlatSpec with Matchers {

  val awsSecretsManagerKeyConfig: AwsSecretsManagerKeyConfig = AwsSecretsManagerKeyConfig("Secret",
    "region",
    "private",
    "public",
    "RS256",
    15.minutes,
    9.minutes,
    Option(30.minutes),
    Option(15.minutes),
    Option(5.minutes))

  "awsSecretsManagerKeyConfig" should "validate expected content" in {
    awsSecretsManagerKeyConfig.validate() shouldBe ConfigValidationSuccess
  }

  it should "fail on invalid algorithm" in {
    awsSecretsManagerKeyConfig.copy(algName = "ABC").validate() shouldBe
      ConfigValidationError(ConfigValidationException("Invalid algName 'ABC' was given."))
  }

  it should "fail on non-negative accessExpTime" in {
    awsSecretsManagerKeyConfig.copy(accessExpTime = 5.milliseconds).validate() shouldBe
      ConfigValidationError(ConfigValidationException(s"accessExpTime must be at least ${KeyConfig.minAccessExpTime}"))
  }

  it should "fail on non-negative refreshExpTime" in {
    awsSecretsManagerKeyConfig.copy(refreshExpTime = 5.milliseconds).validate() shouldBe
      ConfigValidationError(ConfigValidationException(s"refreshExpTime must be at least ${KeyConfig.minRefreshExpTime}"))
  }

  it should "fail on non-negative keyRotationTime" in {
    awsSecretsManagerKeyConfig.copy(pollTime = Option(5.milliseconds)).validate() shouldBe
      ConfigValidationError(ConfigValidationException(s"keyRotationTime must be at least ${KeyConfig.minKeyRotationTime}"))
  }

  it should "fail on non-negative keyPhaseOutTime" in {
    awsSecretsManagerKeyConfig.copy(keyPhaseOutTime = Option(5.milliseconds), keyLayOverTime = None).validate() shouldBe
      ConfigValidationError(ConfigValidationException(s"keyPhaseOutTime must be at least ${KeyConfig.minKeyPhaseOutTime}"))
  }

  it should "fail on keyPhaseOutTime being configured without keyRotationTime" in {
    awsSecretsManagerKeyConfig.copy(pollTime = None, keyLayOverTime = None).validate() shouldBe
      ConfigValidationError(ConfigValidationException(s"keyPhaseOutTime can only be enable if keyRotationTime is enable!"))
  }

  it should "fail on non-negative keyLayOverTime" in {
    awsSecretsManagerKeyConfig.copy(keyLayOverTime = Option(5.milliseconds)).validate() shouldBe
      ConfigValidationError(ConfigValidationException(s"keyLayOverTime must be at least ${KeyConfig.minKeyLayOverTime}"))
  }

  it should "fail on keyLayOverTime being configured without keyRotationTime" in {
    awsSecretsManagerKeyConfig.copy(pollTime = None, keyPhaseOutTime = None).validate() shouldBe
      ConfigValidationError(ConfigValidationException(s"keyLayOverTime can only be enable if keyRotationTime is enable!"))
  }

  it should "fail on missing value" in {
    awsSecretsManagerKeyConfig.copy(secretName = null).validate() shouldBe
      ConfigValidationError(ConfigValidationException("secretName is empty"))
  }
}
