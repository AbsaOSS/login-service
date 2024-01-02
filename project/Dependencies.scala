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

import sbt._

object Dependencies {

  object Versions {
    val jacksonModuleScala = "2.14.2"
    val jacksonDatabind: String = jacksonModuleScala
    val typesafeConfig = "1.4.2"
    val javaCompat = "0.9.0"

    val springBoot = "2.7.8"
    val spring = "5.7.6"

    val jjwt = "0.11.5"

    val nimbusJoseJwt = "9.31"

    val scalatest = "3.2.15"

    val pureConfig = "0.17.2"
  }

  lazy val jacksonModuleScala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % Versions.jacksonModuleScala
  lazy val jacksonDatabind = "com.fasterxml.jackson.core" % "jackson-databind" % Versions.jacksonDatabind
  lazy val javaCompat = "org.scala-lang.modules" %% "scala-java8-compat" % Versions.javaCompat

  lazy val springBootWeb =            "org.springframework.boot" % "spring-boot-starter-web" % Versions.springBoot
  lazy val springBootTomcat =         "org.springframework.boot" % "spring-boot-starter-tomcat" % Versions.springBoot % Provided
  lazy val springBootSecurity =       "org.springframework.boot" % "spring-boot-starter-security" % Versions.springBoot

  lazy val springSecurityLDAP = "org.springframework.security" % "spring-security-ldap" % Versions.spring

  lazy val jjwtApi = "io.jsonwebtoken" % "jjwt-api" % Versions.jjwt
  lazy val jjwtImpl = "io.jsonwebtoken" % "jjwt-impl" % Versions.jjwt % Runtime
  lazy val jjwtJackson = "io.jsonwebtoken" % "jjwt-jackson" % Versions.jjwt % Runtime

  lazy val jsonParser = "com.google.code.gson" % "gson" % "2.10.1"

  lazy val jwtDecoder = "org.springframework.security" % "spring-security-oauth2-jose" % Versions.spring
  lazy val nimbusJoseJwt = "com.nimbusds" % "nimbus-jose-jwt" % Versions.nimbusJoseJwt
  lazy val bouncyCastle = "org.bouncycastle" % "bcprov-jdk15on" % "1.70"

  lazy val pureConfig = "com.github.pureconfig" %% "pureconfig" % Versions.pureConfig
  lazy val pureConfigYaml = "com.github.pureconfig" %% "pureconfig-yaml" % Versions.pureConfig

  lazy val requests = "com.lihaoyi" %% "requests" % "0.8.0"

  lazy val awsSecrets = "software.amazon.awssdk" % "secretsmanager" % "2.20.68"
  lazy val awsSts = "software.amazon.awssdk" % "sts" % "2.20.69"

  lazy val servletApi = "javax.servlet" % "javax.servlet-api" % "3.0.1" % Provided

  // this is UI + swagger annotations together, just annotathons should be in "io.swagger.core.v3":"swagger-annotations":"2.2.8"+
  lazy val springDoc = "org.springdoc" % "springdoc-openapi-ui" % "1.6.14"

  // Enables /actuator/health endpoint
  lazy val springBootStarterActuator = "org.springframework.boot" % "spring-boot-starter-actuator" % Versions.springBoot

  lazy val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalatest % Test
  lazy val springBootTest = "org.springframework.boot" % "spring-boot-starter-test" % Versions.springBoot % Test
  lazy val springBootSecurityTest = "org.springframework.security" % "spring-security-test" % Versions.spring % Test

  def serviceDependencies: Seq[ModuleID] = Seq(
    jacksonModuleScala,
    jacksonDatabind,
    javaCompat,

    springBootWeb,
    springBootTomcat,
    springBootSecurity,

    springSecurityLDAP,

    jjwtApi,
    jjwtImpl,
    jjwtJackson,

    nimbusJoseJwt,

    pureConfig,
    pureConfigYaml,

    awsSecrets,
    awsSts,

    springDoc,

    springBootStarterActuator,

    scalaTest,
    springBootTest,
    springBootSecurityTest
  )

  def clientLibDependencies: Seq[ModuleID] = Seq(
    javaCompat,

    nimbusJoseJwt,
    jwtDecoder,
    bouncyCastle,

    jjwtApi,
    jjwtImpl,
    jjwtJackson,

    jsonParser,

    requests,

    springBootWeb,
    springBootSecurity,

    scalaTest
  )

  def exampleDependencies: Seq[ModuleID] = Seq(
    pureConfig,
    pureConfigYaml
  )
}