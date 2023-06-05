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

package za.co.absa.logingw.rest.actuator.tooling

import java.nio.file.{Files, Paths}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.springframework.boot.test.context.SpringBootTest
import za.co.absa.logingw.rest.config.GitPropertiesGenerator

@SpringBootTest
class GitPropertiesGenerator extends AnyFlatSpec with Matchers {

  ignore should "generate git.properties file" in {
    GitPropertiesGenerator.generateGitProperties()
    assert(Files.exists(Paths.get("service\\src\\main\\resources\\git.properties")))
  }
  }
