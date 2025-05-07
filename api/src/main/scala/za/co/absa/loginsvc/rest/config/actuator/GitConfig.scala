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

package za.co.absa.loginsvc.rest.config.actuator

import java.io.{IOException, PrintWriter}
import java.text.SimpleDateFormat
import scala.util.Try

case class GitConfig(generateGitProperties: Boolean, generateGitPropertiesFile: Boolean) {

  if(generateGitProperties)
    GitPropertiesGenerator.generateGitProperties(generateGitPropertiesFile)
}

object GitPropertiesGenerator {
  private var branch: String = _
  private var commitId: String = _
  private var commitTime: String = _
  private var latestVersion: String = _
  def generateGitProperties(writeFile: Boolean): Unit = {
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    setProperties(
      getGitOutput("git rev-parse --abbrev-ref HEAD").getOrElse("unknown"),
      getGitOutput("git rev-parse HEAD").getOrElse("unknown"),
      dateFormat.format(getGitOutput("git show -s --format=%ct HEAD").map(_.toLong * 1000).getOrElse(0L)),
      getGitOutput("git describe --tags --abbrev=0").getOrElse("unknown")
    )
    if(writeFile)
      writeGitPropertiesToFile()
  }

  def setProperties(branch: String, commitId: String, commitTime: String, latestVersion: String): Unit = {
    this.branch = branch
    this.commitId = commitId
    this.commitTime = commitTime
    this.latestVersion = latestVersion
  }

  def getBranch: String = branch
  def getCommitId: String = commitId
  def getCommitTime: String = commitTime
  def getLatestVersion: String = latestVersion

  private def getGitOutput(command: String): Option[String] = {
    Try(sys.process.Process(command).lineStream_!.headOption)
      .recover {
        case ex: IOException =>
          println(s"Failed to execute Git command: $command")
          None
      }
      .getOrElse(None)
  }

  private def writeGitPropertiesToFile() : Unit = {
    val gitPropertiesFile = "api\\src\\main\\resources\\git.properties"
    val writer = new PrintWriter(gitPropertiesFile)
    val content =
      s"""|git.branch=${this.branch}
          |git.commit.id=${this.commitId}
          |git.commit.time=${this.commitTime}
          |git.build.version=${this.latestVersion}
          |""".stripMargin

    try {
      writer.println(content)
    } catch {
      case ex: IOException =>
        println(s"Failed to write git.properties file: ${ex.getMessage}")
    } finally {
      writer.close()
    }
  }
}
