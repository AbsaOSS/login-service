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

ThisBuild / organizationHomepage := Some(url("https://www.absa.africa"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    browseUrl = url("https://github.com/AbsaOSS/spark-commons/tree/master"),
    connection = "scm:git:git://github.com/AbsaOSS/spark-commons.git",
    devConnection = "scm:git:ssh://github.com/AbsaOSS/spark-commons.git"
  )
)

ThisBuild / developers := List(
  Developer(
    id    = "dk1844",
    name  = "Daniel Kavan",
    email = "daniel.kavan@absa.africa",
    url   = url("https://github.com/dk1844")
  ),
  Developer(
    id = "jakipatryk",
    name = "Bartlomiej Baj",
    email = "bartlomiej.baj@absa.africa",
    url = url("https://github.com/jakipatryk")
  ),
  Developer(
    id = "TheLydonKing",
    name = "Lydon da Rocha",
    email = "lydon.darocha@absa.africa",
    url = url("https://github.com/TheLydonKing")
  )
)

// TODO create a site on github.io  - Issue #1
ThisBuild / homepage := Some(url("https://github.com/AbsaOSS/login-service"))
ThisBuild / description := "Login service for JWT public signing services"

// licenceHeader check:
ThisBuild / organizationName := "ABSA Group Limited"
ThisBuild / startYear := Some(2023)
ThisBuild / licenses += "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt")
