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

package za.co.absa.logingw.rest.config.validation

sealed trait ConfigValidationResult {
  def getErrors: Seq[ConfigValidationException]

  def merge(anotherResult: ConfigValidationResult): ConfigValidationResult = ConfigValidationResult.merge(this, anotherResult)

}

object ConfigValidationResult {

  def merge(res1: ConfigValidationResult, res2: ConfigValidationResult): ConfigValidationResult = (res1, res2) match {
    case (ConfigValidationSuccess, ConfigValidationSuccess) => ConfigValidationSuccess
    case (ConfigValidationSuccess, e@ConfigValidationError(_)) => e
    case (e@ConfigValidationError(_), ConfigValidationSuccess) => e
    case (ConfigValidationError(rs1), ConfigValidationError(rs2)) => ConfigValidationError(rs1 ++ rs2)
  }

  case object ConfigValidationSuccess extends ConfigValidationResult {
    override def getErrors: Seq[ConfigValidationException] = Seq.empty
  }

  case class ConfigValidationError(reasons: Seq[ConfigValidationException]) extends ConfigValidationResult {
    override def getErrors: Seq[ConfigValidationException] = reasons
  }

  case object ConfigValidationError {
    /**
     * Shorthand of za.co.absa.logingw.rest.config.validation.ConfigValidationException(Seq(reason)
     *
     * @param reason validation exception
     * @return ConfigValidationException
     */
    def apply(reason: ConfigValidationException): ConfigValidationError = new ConfigValidationError(Seq(reason))
  }

}

