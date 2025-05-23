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

package za.co.absa.loginsvc.rest.config.actuator

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class GitConfigTest extends AnyFlatSpec with Matchers {

  "The constructor" should " not generate git.properties" in {
    val gitConfig = GitConfig(generateGitProperties = false, generateGitPropertiesFile = false)

    GitPropertiesGenerator.setProperties("Test1", "Test2", "Test3","Test4")
    assert(GitPropertiesGenerator.getBranch == "Test1" &&
      GitPropertiesGenerator.getCommitId == "Test2" &&
      GitPropertiesGenerator.getCommitTime == "Test3" &&
      GitPropertiesGenerator.getLatestVersion == "Test4")
  }

  "The constructor" should "generate git.properties" in {
    GitPropertiesGenerator.setProperties("Test1", "Test2", "Test3","Test4")
    GitPropertiesGenerator.generateGitProperties(false)
    assert(GitPropertiesGenerator.getBranch != "Test1" &&
      GitPropertiesGenerator.getCommitId != "Test2" &&
      GitPropertiesGenerator.getCommitTime != "Test3"&&
      GitPropertiesGenerator.getLatestVersion != "Test4")
  }

}
