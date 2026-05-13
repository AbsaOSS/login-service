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

object PrefixesConfig {
  def fromCommaSeparatedString(prefixesStr: String, caseSensitive: Boolean): PrefixesConfig = {
    val prefixes = prefixesStr.split(",").map(_.trim).filter(_.nonEmpty).toSet
    // TODO add test for empty prefixes given like "prefix1, ,prefix2" or ",prefix1,prefix2," or ",,," etc.
    PrefixesConfig(prefixes, caseSensitive)
  }


}

case class PrefixesConfig(
                           prefixes: Set[String],
                           caseSensitive: Boolean
                         ) {
  /**
   * Filters the given groups based on the defined prefixes and case sensitivity.
   * If there are no prefixes defined, it returns the original groups without filtering.
   *
   * @param groups to potentially filter based on the defined prefixes and case sensitivity
   * @return
   */
  def applyPrefixFiltering(groups: Seq[String]): Seq[String] = {
    if (prefixes.isEmpty) {
      groups // if there are no prefixes, we should not filter out any groups
    } else {
      if (caseSensitive) {
        groups.filter(group => prefixes.exists(group.startsWith))
      } else {
        groups.filter(group => prefixes.map(_.toLowerCase).exists(group.toLowerCase.startsWith))
      }
    }
  }

  def applyPrefixFiltering(groups: Set[String]): Set[String] = applyPrefixFiltering(groups.toSeq).toSet
}

