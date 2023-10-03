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

package za.co.absa.loginsvc.utils

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class OptionUtilsTest extends AnyFlatSpec with Matchers {

  "OptionUtils.applyIfDefined" should "apply fn correctly if defined" in {
    OptionUtils.applyIfDefined(1, Some(2), (a: Int, b: Int) => a + b) shouldBe 3
  }

  it should "not apply fn if empty" in {
    OptionUtils.applyIfDefined(1, None, (a: Int, b: Int) => a + b) shouldBe 1
  }

  import OptionUtils.ImplicitBuilderExt

  "ImplicitBuilderExt.applyIfDefined" should "apply fn correctly if defined" in {
    1.applyIfDefined(Some(2), (a: Int, b: Int) => a + b) shouldBe 3
  }

  it should "not apply fn if empty" in {
    1.applyIfDefined(None, (a: Int, b: Int) => a + b) shouldBe 1
  }

}
