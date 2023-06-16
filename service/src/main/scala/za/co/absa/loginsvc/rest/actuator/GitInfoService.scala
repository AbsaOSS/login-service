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

package za.co.absa.loginsvc.rest.actuator

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.info.{Info, InfoContributor}
import org.springframework.stereotype.Component
import za.co.absa.loginsvc.rest.config.GitPropertiesGenerator

@Component
class GitInfoService(@Value("${management.info.git.enabled:false}")
                     gitInfoEnabled: Boolean,
                     @Value("${logingw.rest.config.git-info.generate-git-properties:false}")
                     gitGenerationEnabled: Boolean) extends InfoContributor {

  override def contribute(builder: Info.Builder): Unit = {

    if(gitInfoEnabled && gitGenerationEnabled) {
      builder.withDetail("git", Map(
        "branch" -> GitPropertiesGenerator.getBranch,
        "commit" -> Map(
          "id" -> GitPropertiesGenerator.getCommitId,
          "time" -> GitPropertiesGenerator.getCommitTime
        )
      ))
    }
  }
}
