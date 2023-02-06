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
    val jacksonDatabind = jacksonModuleScala
    val typesafeConfig = "1.4.2"
    val javaCompat = "0.9.0"

    val springBoot = "2.7.8"

    val jjwt = "0.11.5"

    val scalatest = "3.2.15"
  }

  lazy val jacksonModuleScala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % Versions.jacksonModuleScala
  lazy val jacksonDatabind = "com.fasterxml.jackson.core" % "jackson-databind" % Versions.jacksonDatabind
  lazy val typeSafeConfig = "com.typesafe" % "config" % Versions.typesafeConfig
  lazy val javaCompat = "org.scala-lang.modules" %% "scala-java8-compat" % Versions.javaCompat

  lazy val springBootWeb =            "org.springframework.boot" % "spring-boot-starter-web" % Versions.springBoot
  lazy val springBootConfiguration =  "org.springframework.boot" % "spring-boot-configuration-processor" % Versions.springBoot
  lazy val springBootTomcat =         "org.springframework.boot" % "spring-boot-starter-tomcat" % Versions.springBoot % Provided
  lazy val springBootSecurity =       "org.springframework.boot" % "spring-boot-starter-security" % Versions.springBoot

  lazy val jjwtApi = "io.jsonwebtoken" % "jjwt-api" % Versions.jjwt
  lazy val jjwtImpl = "io.jsonwebtoken" % "jjwt-impl" % Versions.jjwt % Runtime
  lazy val jjwtJackson = "io.jsonwebtoken" % "jjwt-jackson" % Versions.jjwt % Runtime

  lazy val servletApi = "javax.servlet" % "javax.servlet-api" % "3.0.1" % Provided


  // TODO bring actuator (health) endpoints in? - Issue #6
  //lazy val springBootStarterActuator = "org.springframework.boot" % "spring-boot-starter-actuator" % Versions.springBoot

  // TODO LDAP/Kerberos integration: - Issue #8
  // org.springframework.boot % {spring-boot-starter-security, spring-security-ldap, }
  // org.springframework.security.kerberos % {spring-security-kerberos-web, spring-security-kerberos-client}


  lazy val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalatest % Test
  lazy val springBootTest = "org.springframework.boot" % "spring-boot-starter-test" % Versions.springBoot

  def serviceDependencies: Seq[ModuleID] = Seq(
    jacksonModuleScala,
    jacksonDatabind,
    typeSafeConfig,
    javaCompat,

    springBootWeb,
    springBootConfiguration,
    springBootTomcat,
    springBootSecurity,

    jjwtApi,
    jjwtImpl,
    jjwtJackson,

    scalaTest,
    springBootTest
  )

}
