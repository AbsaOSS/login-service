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

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.springframework.boot.actuate.info.Info.Builder
import org.springframework.boot.test.context.SpringBootTest
import org.mockito.Mockito._
import za.co.absa.loginsvc.rest.config.actuator.GitPropertiesGenerator
import za.co.absa.loginsvc.rest.service.actuator.GitInfoService

@SpringBootTest
class GitInfoServiceTest extends AnyFlatSpec with Matchers {

  GitPropertiesGenerator.setProperties("Test1","Test2","Test3")
  private val infoService: GitInfoService = new GitInfoService(true, true)

  "GitInfoService" should "contribute git information" in {
    val builderMock: Builder = mock(classOf[Builder])

    infoService.contribute(builderMock)

    val expectedDetails = Map(
      "branch" -> GitPropertiesGenerator.getBranch,
      "commit" -> Map(
        "id" -> GitPropertiesGenerator.getCommitId,
        "time" -> GitPropertiesGenerator.getCommitTime
      )
    )
    verify(builderMock).withDetail("git", expectedDetails)
  }
}
