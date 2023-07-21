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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.{HealthEndpoint, Status}
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.{TestContextManager, TestPropertySource}

@SpringBootTest
@TestPropertySource(properties = Array("DefaultYamlPath=service/src/test/resources/application.yaml"))
class ActuatorHealthTest extends AnyFlatSpec with Matchers {

  @Autowired
  private var healthService: HealthEndpoint = _

  // Makes the above autowired work
  new TestContextManager(this.getClass).prepareTestInstance(this)

  "The Overall HealthEndpoint Status" should "return UP" in {
    val health = healthService.health()
    assert(health.getStatus == Status.UP)
  }
  //TODO: Add more tests for each dependency (Example Ldap when implemented fully) - issue #20
}
