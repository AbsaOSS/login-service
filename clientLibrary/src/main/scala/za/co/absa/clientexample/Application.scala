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

package za.co.absa.clientexample

import org.springframework.security.oauth2.jwt.{Jwt, JwtDecoder}
import za.co.absa.clientexample.config.ConfigProvider
import za.co.absa.loginclient.authorization.JWTDecoderProvider
import za.co.absa.loginclient.tokenRetrieval.service.RetrieveToken

import java.util.Scanner

object Application {
  def main(args: Array[String]): Unit = {

    val config = new ConfigProvider("clientLibrary/src/main/resources/exampleConfig.yaml").
      getExampleConfig

    val tokenRetriever = RetrieveToken(config.host)
    val scanner = new Scanner(System.in)

    while (true) {
      // Ask the user for their username and password
      print("Enter your username: ")
      val username = scanner.nextLine()
      print("Enter your password: ")
      val password = scanner.nextLine()

      try {
        val jwt = tokenRetriever.fetchAccessToken(username, password)
        val jwtDecoder = JWTDecoderProvider(config.host, config.refreshPeriod)
        val claims = jwtDecoder.verifyAccessToken(jwt)
        println(s"${claims("sub")} has authenticated successfully.")
      }
      catch {
        case e: Throwable =>
          println("Authentication failed. Please try again.")
      }
    }
  }
}