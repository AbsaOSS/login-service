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

class OptionExtTest extends AnyFlatSpec with Matchers {

  "OptionExt.applyIfDefined" should "apply fn correctly if defined" in {
    OptionExt.applyIfDefined(1, Some(2), (a:Int, b: Int) => a + b) shouldBe 3
  }

  it should "not apply fn if empty" in {
    OptionExt.applyIfDefined(1, None, (a: Int, b: Int) => a + b) shouldBe 1
  }

  import OptionExt.ImplicitOptionExt

  "OptionExt.ImplicitOptionExt" should "apply fn correctly if defined" in {
    Some(2).applyIfDefined(1, (a: Int, b: Int) => a + b) shouldBe 3
  }

  it should "not apply fn if empty" in {
    None.applyIfDefined(1, (a: Int, b: Int) => a + b) shouldBe 1
  }

}
