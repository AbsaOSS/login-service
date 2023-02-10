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

package za.co.absa.logingw.rest

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.swagger.v3.oas.annotations.info.{Info, License}
import io.swagger.v3.oas.annotations.{ExternalDocumentation, OpenAPIDefinition}
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.context.annotation._

@OpenAPIDefinition(
  externalDocs = new ExternalDocumentation(description = "GitHub",
    url = "https://github.com/AbsaOSS/login-gateway"
  ),
  info = new Info(
    title = "Login Gateway API",
    version = "0.1",
    license = new License(name = "Apache 2.0", url = "https://www.apache.org/licenses/LICENSE-2.0.html")
  )
)
@SpringBootApplication
@Configuration
class Application extends SpringBootServletInitializer {

  override def configure(application: SpringApplicationBuilder): SpringApplicationBuilder =
    application.sources(classOf[Application])

  @Bean
  def objectMapper(): ObjectMapper = {
    new ObjectMapper()
      .registerModule(DefaultScalaModule)
      .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
  }
}

object Application extends App {
  SpringApplication.run(classOf[Application], args: _ *)
}
