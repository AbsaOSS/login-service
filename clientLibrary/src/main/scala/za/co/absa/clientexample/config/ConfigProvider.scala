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

package za.co.absa.clientexample.config

import pureconfig._
import pureconfig.generic.auto._
import pureconfig.module.yaml._

class ConfigProvider(yamlPath: String) {

  private val yamlConfig: YamlConfigSource = YamlConfigSource.file(yamlPath)

  def getExampleConfig: ExampleConfig = {
    createConfigClass[ExampleConfig]("login-service.example").
      getOrElse(throw new Exception(s"Config Not Available. Please check $yamlPath"))
  }
  private def createConfigClass[A](nameSpace: String)(implicit reader: ConfigReader[A]): Option[A] = {
    val configProperty: ConfigSource = this.yamlConfig.at(nameSpace)
    val configClass: Option[A] = configProperty.load[A].toOption
    if (configProperty.value().isRight && configClass.isEmpty)
      throw new Exception(s"Config properties $nameSpace found but could not be parsed, please check if correct")

    configClass
  }
}
