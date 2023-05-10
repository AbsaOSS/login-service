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

package za.co.absa.logingw.rest.config

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.logingw.rest.config.validation.ConfigValidationException
import za.co.absa.logingw.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}

class JwtConfigTest extends AnyFlatSpec with Matchers {

  val jwtConfig = JwtConfig("RS256", 2)

  "JwtConfig" should "validate expected content" in {
    jwtConfig.validate() shouldBe ConfigValidationSuccess
  }

  it should "fail on invalid algorithm" in {
    jwtConfig.copy(algName = "ABC").validate() shouldBe
      ConfigValidationError(ConfigValidationException("Invalid algName 'ABC' was given."))
  }

  it should "fail on non-negative expTime" in {
    jwtConfig.copy(expTime = -7).validate() shouldBe
      ConfigValidationError(ConfigValidationException("expTime must be positive (hours)"))

  }

}
