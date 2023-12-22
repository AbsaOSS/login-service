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

import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import za.co.absa.clientexample.config.ConfigProvider
import za.co.absa.loginclient.authorization.ClaimsParser.{getAllClaims, verifyDecodedAccessToken}
import za.co.absa.loginclient.publicKeyRetrieval.client.PublicKeyRetrievalClient
import za.co.absa.loginclient.tokenRetrieval.client.TokenRetrievalClient

import java.nio.file.{Files, Paths}
import java.util.Scanner
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object Application {

  def main(args: Array[String]): Unit = {

    var configPath = ""
    if (args.length < 1) {
      throw new Exception("Usage: Application <config_path>")
    } else {
      if(Files.exists(Paths.get(args(0)))) configPath = args(0)
      else throw new Exception("Config file does not exist")
    }
    val config = new ConfigProvider(configPath).getExampleConfig

    val tokenRetriever = TokenRetrievalClient(config.host)
    val publicKeyRetriever = PublicKeyRetrievalClient(config.host)
    val scanner = new Scanner(System.in)

    val jwtDecoder = {
      val publicKey = publicKeyRetriever.getPublicKey
      val publicKeyBytes = Base64.getDecoder.decode(publicKey.token)
      val publicKeySpec = new X509EncodedKeySpec(publicKeyBytes)
      val encodedPublicKey = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec).asInstanceOf[RSAPublicKey]
      NimbusJwtDecoder.withPublicKey(encodedPublicKey).build()
    }

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
        val accessJwt = jwtDecoder.decode(accessToken.token)
        var accessClaims = getAllClaims(accessJwt)
        if(!verifyDecodedAccessToken(accessClaims)) throw new Exception("Access Token Verification Failed")
        loggedIn = true
        println("----------------------------------------------")
        println(s"${accessClaims("sub").toString.toUpperCase} HAS LOGGED IN.")
        println(s"ACCESS TOKEN: $accessToken")
        println(s"REFRESH TOKEN: $refreshToken")
        println("----------------------------------------------")

        while (loggedIn) {
          println("1) Refresh Token")
          println("2) Print Claims")
          println("3) Logout")
          print("Enter your choice: ")
          val choice = scanner.nextLine()
          choice match {
            case "1" =>
              val (newAccessToken, newRefreshToken) = tokenRetriever.refreshAccessToken(accessToken, refreshToken)
              accessClaims = getAllClaims(accessJwt)
              if(!verifyDecodedAccessToken(accessClaims)) throw new Exception("Refreshed Access Token Verification Failed")
              println("----------------------------------------------")
              println(s"NEW ACCESS TOKEN: $newAccessToken")
              println(s"REFRESH TOKEN: $newRefreshToken")
              println(s"${accessClaims("sub").toString.toUpperCase} HAS REFRESHED ACCESS TOKEN.")
              println("----------------------------------------------")
            case "2" =>
              println("----------------------------------------------")
              println(s"CLAIMS: $accessClaims")
              println("----------------------------------------------")
            case "3" =>
              loggedIn = false
              println("----------------------------------------------")
              println(s"${accessClaims("sub").toString.toUpperCase} HAS LOGGED OUT.")
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