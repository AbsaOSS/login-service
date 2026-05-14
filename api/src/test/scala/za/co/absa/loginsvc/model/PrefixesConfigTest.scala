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

class PrefixesConfigTest extends AnyFlatSpec with Matchers {

  "PrefixesConfig.fromCommaSeparatedString" should "parse a normal comma-separated string" in {
    val config = PrefixesConfig.fromCommaSeparatedString("prefix1,prefix2,prefix3", caseSensitive = true)
    config.prefixes shouldBe Set("prefix1", "prefix2", "prefix3")
    config.caseSensitive shouldBe true
  }

  it should "trim whitespace around prefixes" in {
    val config = PrefixesConfig.fromCommaSeparatedString("  prefix1 , prefix2 , prefix3  ", caseSensitive = false)
    config.prefixes shouldBe Set("prefix1", "prefix2", "prefix3")
  }

  it should "filter out empty entries from 'prefix1, ,prefix2'" in {
    val config = PrefixesConfig.fromCommaSeparatedString("prefix1, ,prefix2", caseSensitive = true)
    config.prefixes shouldBe Set("prefix1", "prefix2")
  }

  it should "filter out empty entries from ',prefix1,prefix2,'" in {
    val config = PrefixesConfig.fromCommaSeparatedString(",prefix1,prefix2,", caseSensitive = true)
    config.prefixes shouldBe Set("prefix1", "prefix2")
  }

  it should "produce empty prefixes from ',,,'" in {
    val config = PrefixesConfig.fromCommaSeparatedString(",,,", caseSensitive = true)
    config.prefixes shouldBe empty
  }

  it should "produce empty prefixes from an empty string" in {
    val config = PrefixesConfig.fromCommaSeparatedString("", caseSensitive = true)
    config.prefixes shouldBe empty
  }

  it should "handle a single prefix without commas" in {
    val config = PrefixesConfig.fromCommaSeparatedString("onlyOne", caseSensitive = false)
    config.prefixes shouldBe Set("onlyOne")
  }

  private val groups = Seq("blue-123", "blue-456", "red-ABC", "REDdish-DEF", "black", "black-and-white")

  "PrefixesConfig.applyPrefixFiltering" should "filter case-sensitively" in {
    val config = PrefixesConfig(Set("red", "black"), caseSensitive = true)
    config.applyPrefixFiltering(groups) shouldBe Seq("red-ABC", "black", "black-and-white")
  }

  it should "filter case-insensitively" in {
    val config = PrefixesConfig(Set("RED", "BLACK"), caseSensitive = false)
    config.applyPrefixFiltering(groups) shouldBe Seq("red-ABC", "REDdish-DEF", "black", "black-and-white")
  }

  it should "return all groups when prefixes are empty" in {
    val config = PrefixesConfig(Set.empty, caseSensitive = true)
    config.applyPrefixFiltering(groups) shouldBe groups
  }

  it should "return empty when no groups match" in {
    val config = PrefixesConfig(Set("yellow", "green"), caseSensitive = true)
    config.applyPrefixFiltering(groups) shouldBe empty
  }

  it should "handle empty groups input" in {
    val config = PrefixesConfig(Set("red"), caseSensitive = true)
    config.applyPrefixFiltering(Seq.empty[String]) shouldBe empty
  }

  private val groupsSet = Set("blue-123", "blue-456", "red-ABC", "REDdish-DEF", "black", "black-and-white")

  "PrefixesConfig.applyPrefixFiltering(Set)" should "filter case-sensitively" in {
    val config = PrefixesConfig(Set("red", "black"), caseSensitive = true)
    config.applyPrefixFiltering(groupsSet) shouldBe Set("red-ABC", "black", "black-and-white")
  }

  it should "filter case-insensitively" in {
    val config = PrefixesConfig(Set("RED", "BLACK"), caseSensitive = false)
    config.applyPrefixFiltering(groupsSet) shouldBe Set("red-ABC", "REDdish-DEF", "black", "black-and-white")
  }

  it should "return all groups when prefixes are empty" in {
    val config = PrefixesConfig(Set.empty, caseSensitive = true)
    config.applyPrefixFiltering(groupsSet) shouldBe groupsSet
  }

  it should "return empty when no groups match" in {
    val config = PrefixesConfig(Set("yellow", "green"), caseSensitive = true)
    config.applyPrefixFiltering(groupsSet) shouldBe empty
  }

  it should "handle empty groups input" in {
    val config = PrefixesConfig(Set("red"), caseSensitive = true)
    config.applyPrefixFiltering(Set.empty[String]) shouldBe empty
  }
}

