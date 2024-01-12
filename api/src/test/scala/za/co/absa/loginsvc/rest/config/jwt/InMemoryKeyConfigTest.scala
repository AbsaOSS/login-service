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

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

class InMemoryKeyConfigTest extends AnyFlatSpec with Matchers {

  val inMemoryKeyConfig: InMemoryKeyConfig = InMemoryKeyConfig("RS256",
    15.minutes,
    2.hours,
    Option(30.minutes))

  "inMemoryKeyConfig" should "validate expected content" in {
    inMemoryKeyConfig.validate() shouldBe ConfigValidationSuccess
  }

  it should "fail on invalid algorithm" in {
    inMemoryKeyConfig.copy(algName = "ABC").validate() shouldBe
      ConfigValidationError(ConfigValidationException("Invalid algName 'ABC' was given."))
  }

  it should "fail on non-negative accessExpTime" in {
    inMemoryKeyConfig.copy(accessExpTime = FiniteDuration(5, TimeUnit.MILLISECONDS)).validate() shouldBe
      ConfigValidationError(ConfigValidationException(s"accessExpTime must be at least ${KeyConfig.minAccessExpTime}"))
  }

  it should "fail on non-negative refreshExpTime" in {
    inMemoryKeyConfig.copy(refreshExpTime = FiniteDuration(5, TimeUnit.MILLISECONDS)).validate() shouldBe
      ConfigValidationError(ConfigValidationException(s"refreshExpTime must be at least ${KeyConfig.minRefreshExpTime}"))
  }

  it should "fail on non-negative keyRotationTime" in {
    inMemoryKeyConfig.copy(keyRotationTime = Option(FiniteDuration(5, TimeUnit.MILLISECONDS))).validate() shouldBe
      ConfigValidationError(ConfigValidationException(s"keyRotationTime must be at least ${KeyConfig.minKeyRotationTime}"))
  }
}
