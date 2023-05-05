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

package za.co.absa.logingw.rest.service

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestContextManager
import za.co.absa.logingw.rest.config.BaseConfig



@SpringBootTest
class InfoServiceTest extends AnyFlatSpec with Matchers {

  @Autowired
  private var infoService: InfoService = _
  private val testConfig = BaseConfig(algName = "RS256", expTime = 2)

  // Makes the above autowired work
  new TestContextManager(this.getClass).prepareTestInstance(this)

  "InfoService" should "give expected test message" in {
    infoService.getInfoMessage shouldEqual s"Basic info message to be here. '${testConfig.someKey}'"
  }

}
