/*
 * Copyright 2023 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.absa.logingw

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.{Info, License}
import org.springframework.context.annotation.{Bean, Configuration}

@Configuration
class SwaggerConfig {

  @Bean
  def springShopOpenAPI(): OpenAPI = {
    new OpenAPI()
      .info(new Info().title("Login Gateway API")
        .version("v0.0.1")
        .description("AbsaOss Login Gateway service for JWT Asymmetric signing")
        .license(new License().name("Apache 2.0")))

    // todo fill in package-version
    // TODO #1 fill in homepage
    //.externalDocs(new ExternalDocumentation()
    //.description("Documentation HP")
    //.url("https://absaoss.github.io/login-gateway-info-page-goes-here"));
  }

}
