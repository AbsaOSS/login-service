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

    var loggedIn = false

    while (true) {
      println("----------------------------------------------")
      println("---------------PLEASE LOGIN-------------------")
      println("----------------------------------------------")
      print("Enter your username: ")
      val username = scanner.nextLine()
      print("Enter your password: ")
      val password = scanner.nextLine()

      try {
        val (accessToken, refreshToken) = tokenRetriever.fetchAccessAndRefreshToken(username, password)
        val jwtDecoder = JWTDecoderProvider(config.host, config.refreshPeriod)
        val claims = jwtDecoder.verifyAccessToken(accessToken)
        loggedIn = true
        println("----------------------------------------------")
        println(s"${claims("sub").toString.toUpperCase} HAS LOGGED IN.")
        println(s"ACCESS TOKEN: $accessToken")
        println("----------------------------------------------")

        while(loggedIn) {
          println("1) Refresh Token")
          println("2) Print Claims")
          println("3) Logout")
          print("Enter your choice: ")
          val choice = scanner.nextLine()
          choice match {
            case "1" =>
                val (newAccessToken, newRefreshToken) = tokenRetriever.refreshAccessToken(accessToken, refreshToken)
                val newClaims = jwtDecoder.verifyAccessToken(newAccessToken)
                println("----------------------------------------------")
                println(s"NEW ACCESS TOKEN: $newAccessToken")
                println(s"${newClaims("sub").toString.toUpperCase} HAS REFRESHED ACCESS TOKEN.")
                println("----------------------------------------------")
            case "2" =>
              println("----------------------------------------------")
              println(s"CLAIMS: $claims")
              println("----------------------------------------------")
            case "3" =>
              loggedIn = false
              println("----------------------------------------------")
              println(s"${claims("sub").toString.toUpperCase} HAS LOGGED OUT.")
              println("----------------------------------------------")
            case _ =>
              println("----------------------------------------------")
              println(s"INVALID CHOICE. PLEASE TRY AGAIN")
              println("----------------------------------------------")
          }
        }
      }
      catch {
        case e: Throwable =>
          println("----------------------------------------------")
          println(s"UNAUTHORIZED. PLEASE TRY AGAIN")
          println("----------------------------------------------")
      }
    }
  }
}