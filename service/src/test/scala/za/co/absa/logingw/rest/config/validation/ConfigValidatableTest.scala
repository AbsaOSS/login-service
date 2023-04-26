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

package za.co.absa.logingw.rest.config.validation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.logingw.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}

class ConfigValidatableTest extends AnyFlatSpec with Matchers {

  "ConfigValidatable" should "throw with .failOnValidationError with errors" in {

    val errorousConfigValidatable = new ConfigValidatable {
      override def validate(): ConfigValidationResult =
        ConfigValidationError(Seq(ConfigValidationException("one"), ConfigValidationException("two")))
    }

    intercept[ConfigValidationException] {
      errorousConfigValidatable.failOnValidationError()
    }.msg shouldBe "one" // first exc get propagated
  }

  it should "not throw with .failOnValidationError without errors" in {

    val errorlessConfigValidatable = new ConfigValidatable {
      override def validate(): ConfigValidationResult = ConfigValidationSuccess
    }

    errorlessConfigValidatable.failOnValidationError() // does not throw
  }

}
