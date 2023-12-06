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

import Dependencies._
import com.github.sbt.jacoco.report.JacocoReportSettings

ThisBuild / organization := "za.co.absa"
ThisBuild / name         := "login-service"

lazy val scala212 = "2.12.17"

ThisBuild / scalaVersion := scala212
ThisBuild / versionScheme := Some("early-semver")

lazy val commonJacocoReportSettings: JacocoReportSettings = JacocoReportSettings(
  formats = Seq(JacocoReportFormats.HTML, JacocoReportFormats.XML)
)

lazy val commonJacocoExcludes: Seq[String] = Seq(
  "za.co.absa.loginsvc.rest.Application*"
  //    "za.co.absa.loginsvc.rest.config.BaseConfig" // class only
)

lazy val parent = (project in file("."))
  .aggregate(service, clientLibrary, examples)
  .settings(
    name := "login-service",
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint"),
    // No need to publish the aggregation [empty] artifact
    publishArtifact := false,
    publish := {},
    publishLocal := {}
  )

lazy val service = project // no need to define file, because path is same as val name
  .settings(
    name := "login-service-service",
    libraryDependencies ++= serviceDependencies,
    webappWebInfClasses := true,
    inheritJarManifest := true,
    // No need to publish the service
    publishArtifact := false,
    publish := {},
    publishLocal := {}
  )
  .settings(
    jacocoReportSettings := commonJacocoReportSettings.withTitle(s"login-service:service Jacoco Report - scala:${scalaVersion.value}"),
    jacocoExcludes := commonJacocoExcludes
  ).enablePlugins(TomcatPlugin)
  .enablePlugins(AutomateHeaderPlugin)

lazy val clientLibrary = project // no need to define file, because path is same as val name
  .settings(
    name := "login-service-client-library",
    libraryDependencies ++= clientLibDependencies
  ).enablePlugins(AutomateHeaderPlugin)

lazy val examples = project // no need to define file, because path is same as val name
  .settings(
    name := "login-service-examples",
    libraryDependencies ++= exampleDependencies,
    // No need to publish the example artifact
    publishArtifact := false,
    publish := {},
    publishLocal := {}
  ).enablePlugins(AutomateHeaderPlugin)