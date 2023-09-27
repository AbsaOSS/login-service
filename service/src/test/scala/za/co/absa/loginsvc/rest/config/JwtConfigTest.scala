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
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationException
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}

import scala.concurrent.duration._

class JwtConfigTest extends AnyFlatSpec with Matchers {

  val jwtConfig = JwtConfig("RS256", 20.minutes, 2.hours)

  "JwtConfig" should "validate expected content" in {
    jwtConfig.validate() shouldBe ConfigValidationSuccess
  }

  it should "fail on invalid algorithm" in {
    jwtConfig.copy(algName = "ABC").validate() shouldBe
      ConfigValidationError(ConfigValidationException("Invalid algName 'ABC' was given."))
  }

  it should "fail on non-negative accessExpTime" in {
    jwtConfig.copy(accessExpTime = -7.hours).validate() shouldBe
      ConfigValidationError(ConfigValidationException("accessExpTime must be at least 5 seconds")) // 5s = test/resources/application.yaml
  }

  it should "fail on non-negative refreshExpTime" in {
    jwtConfig.copy(refreshExpTime = -7.hours).validate() shouldBe
      ConfigValidationError(ConfigValidationException("refreshExpTime must be at least 10 seconds")) // dtto
  }

}
