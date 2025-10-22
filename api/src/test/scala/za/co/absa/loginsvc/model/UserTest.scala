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

package za.co.absa.loginsvc.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class UserTest extends AnyFlatSpec with Matchers {

  val testUser: User = User("testUser", groups = Seq(
    "blue-123",
    "blue-256",
    "red-ABC",
    "REDdish-DEF",
    "black",
    "black-and-white"
  ), Map.empty[String, Option[AnyRef]])

  "User" should "filterGroups by prefixes (case-sensitively)" in {
    testUser.filterGroupsByPrefixes(Set("red", "black", "yellow"), `case-sensitive` = true) shouldBe
      testUser.copy(groups = Seq("red-ABC", "black","black-and-white"))
  }

  it should "filterGroups by prefixes (case-insensitively)" in {
    testUser.filterGroupsByPrefixes(Set("red", "BLaCK", "yellow"), `case-sensitive` = false) shouldBe
      testUser.copy(groups = Seq("red-ABC", "REDdish-DEF", "black","black-and-white"))
  }


}
