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

import za.co.absa.clientexample.config.ConfigProvider
import za.co.absa.loginclient.exceptions.LsJwtException
import za.co.absa.loginclient.authorization.{AccessTokenClaimsParser, AccessTokenVerificator, JwtDecoderProvider}
import za.co.absa.loginclient.tokenRetrieval.client.TokenRetrievalClient
import za.co.absa.loginclient.tokenRetrieval.model.{BasicAuth, KerberosAuth}

import java.nio.file.{Files, Paths}
import java.util.Scanner
import util.control.Breaks._

object Application {

  def main(args: Array[String]): Unit = {

    var configPath = ""
    if (args.length < 1) {
      throw new Exception("Usage: Application <config_path>")
    } else {
      if (Files.exists(Paths.get(args(0)))) configPath = args(0)
      else throw new Exception("Config file does not exist")
    }
    val config = new ConfigProvider(configPath).getExampleConfig

    val tokenRetriever = TokenRetrievalClient(config.host)
    val decoder = JwtDecoderProvider.getDecoderFromURL(config.host)
    val accessVerificator = AccessTokenVerificator(decoder)
    val scanner = new Scanner(System.in)

    var loggedIn = false

    while (true) {
      var username = ""
      var password = ""
      var authMethod = ""
      breakable
      {
        while(true)
        {
          if(config.kerberos.nonEmpty)
            {
              val kerberosConfig = config.kerberos.get
              tokenRetriever.setKerberosProperties(kerberosConfig.jaasFileLocation, kerberosConfig.krbFileLocation, kerberosConfig.debug)
              println("Please choose authentication method:")
              println("1) Basic Authentication")
              println("2) SPNEGO")
              print("Enter your choice: ")
              authMethod = scanner.nextLine()
            }
          else
            {
              authMethod = "1"
            }
          authMethod match {
            case "1" =>
              println("----------------------------------------------")
              println("---------------PLEASE LOGIN-------------------")
              println("----------------------------------------------")
              println("")
              print("Enter your username: ")
              username = scanner.nextLine()
              print("Enter your password: ")
              password = scanner.nextLine()
              break
            case "2" =>
              break
            case _ =>
              println("----------------------------------------------")
              println(s"INVALID CHOICE. PLEASE TRY AGAIN")
              println("----------------------------------------------")
          }
        }
      }

      try {
        val (accessToken, refreshToken) = authMethod match {
          case "1" =>
            val basicAuth = BasicAuth(username, password)
            tokenRetriever.fetchAccessAndRefreshToken(basicAuth)
          case "2" =>
            val kerberosAuth = KerberosAuth(None, None)
            tokenRetriever.fetchAccessAndRefreshToken(kerberosAuth)
        }
        val decodedAtJwt = accessVerificator.decodeAndVerifyAccessToken(accessToken) // throw Exception on verification fail
        loggedIn = true

        var accessClaims = AccessTokenClaimsParser.getAllClaims(decodedAtJwt)

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
              try {
                val refreshedAtJwt = accessVerificator.decodeAndVerifyAccessToken(accessToken)
                accessClaims = AccessTokenClaimsParser.getAllClaims(refreshedAtJwt)
                println("----------------------------------------------")
                println(s"NEW ACCESS TOKEN: $newAccessToken")
                println(s"REFRESH TOKEN: $newRefreshToken")
                println(s"${accessClaims("sub").toString.toUpperCase} HAS REFRESHED ACCESS TOKEN.")
                println("----------------------------------------------")

              } catch {
                case _:LsJwtException =>
                  loggedIn = false
                  println("----------------------------------------------")
                  println(s"REFRESH TOKEN NOT VALID. PLEASE LOG IN AGAIN.")
                  println(s"${accessClaims("sub").toString.toUpperCase} HAS LOGGED OUT.")
                  println("----------------------------------------------")
              }
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
