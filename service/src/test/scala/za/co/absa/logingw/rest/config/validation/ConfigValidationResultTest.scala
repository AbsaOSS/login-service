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

class ConfigValidationResultTest extends AnyFlatSpec with Matchers {

  private val cvExc1 = ConfigValidationException("exception1")
  private val cvExc2 = ConfigValidationException("exception2")

  "ConfigValidationResult" should "merge results correctly" in {

    ConfigValidationSuccess.merge(ConfigValidationSuccess) shouldBe ConfigValidationSuccess
    ConfigValidationSuccess.merge(ConfigValidationError(cvExc1)) shouldBe ConfigValidationError(cvExc1)
    ConfigValidationError(cvExc1).merge(ConfigValidationSuccess) shouldBe ConfigValidationError(cvExc1)

    ConfigValidationError(cvExc1).merge(ConfigValidationError(cvExc2)) shouldBe
      ConfigValidationError(Seq(cvExc1, cvExc2)
      )
  }

  it should "getErrors based on implementation" in {
    ConfigValidationSuccess.getErrors shouldBe empty
    ConfigValidationError(Seq(cvExc1, cvExc2)).getErrors shouldBe Seq(cvExc1, cvExc2)
  }

  "ConfigValidationError(ConfigValidationException)" should
    "be a shorthand for ConfigValidationError(reasons: Seq[ConfigValidationException])" in {
      ConfigValidationError(cvExc1) shouldBe ConfigValidationError(Seq(cvExc1))
  }

}
