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

import org.slf4j.LoggerFactory

trait ConfigValidatable {

  private val logger = LoggerFactory.getLogger(classOf[ConfigValidatable])

  def validate(): ConfigValidationResult

  /**
   * @throws ConfigValidationException if `validate()` returns any errors
   */
  def failOnValidationError(): Unit = {
    val errors = validate().getErrors

    if(errors.nonEmpty) {
      // all errors are logged
      logger.error(s"validation errors (${errors.length}):\n  ${errors.mkString(",\n  ")}")
      // but only the first is in the error stack
      throw errors.head
    }

  }

}
