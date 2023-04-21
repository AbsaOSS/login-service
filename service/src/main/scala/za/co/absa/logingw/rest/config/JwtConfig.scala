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

package za.co.absa.logingw.rest.config

import io.jsonwebtoken.SignatureAlgorithm
import org.springframework.boot.context.properties.{ConfigurationProperties, ConstructorBinding}

import javax.annotation.PostConstruct
import scala.util.{Failure, Try, Success}

@ConstructorBinding
@ConfigurationProperties(prefix = "logingw.rest.jwt")
case class JwtConfig(
  algName: String,
  expTime: Int
) extends ConfigValidatable {

  @PostConstruct
  def init() {
    this.validate()
  }

  /** May throw ConfigValidationException or IllegalArgumentException */
  override def validate(): Unit = {
    Try {
      SignatureAlgorithm.valueOf(algName)
    } match {
      case Success(_) =>
      case Failure(e: IllegalArgumentException) if e.getMessage.contains("No enum constant") =>
        throw new ConfigValidationException(s"Invalid algName '$algName' was given.")
      case Failure(e) => throw e
    }

    if (expTime < 1) throw new ConfigValidationException("expTime must be positive (hours)")
  }
}
