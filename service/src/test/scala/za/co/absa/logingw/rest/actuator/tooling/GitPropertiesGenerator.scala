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

package za.co.absa.logingw.rest.actuator.tooling

import java.io.{PrintWriter, IOException}
import java.nio.file.{Files, Paths}
import java.text.SimpleDateFormat
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.springframework.boot.test.context.SpringBootTest
import scala.util.Try

@SpringBootTest
class GitPropertiesGenerator extends AnyFlatSpec with Matchers {

  ignore should "generate git.properties file" in {
    this.GitPropertiesGenerator.generateGitProperties()
    assert(Files.exists(Paths.get("service\\src\\main\\resources\\git.properties")))
  }

    object GitPropertiesGenerator {
      def generateGitProperties(): Unit = {
        val gitPropertiesFile = "service\\src\\main\\resources\\git.properties"
        val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        val gitBranch = getGitOutput("git rev-parse --abbrev-ref HEAD")
        val gitCommitMessage = getGitOutput("git log -1 --pretty=%B")
        val gitCommitId = getGitOutput("git rev-parse HEAD")
        val gitCommitTime = dateFormat.format(getGitOutput("git show -s --format=%ct HEAD").map(_.toLong * 1000).getOrElse(0L))
        val gitAuthor = getGitOutput("git show -s --format='%ae'")
        val content =
          s"""|git.branch=${gitBranch.getOrElse("unknown")}
              |git.commit.message.full=${gitCommitMessage.getOrElse("unknown")}
              |git.commit.id=${gitCommitId.getOrElse("unknown")}
              |git.commit.time=$gitCommitTime
              |git.commit.user.email=${gitAuthor.getOrElse("unknown")}
              |""".stripMargin

        val writer = new PrintWriter(gitPropertiesFile)

        try {
          writer.println(content)
        } catch {
          case ex: IOException =>
            println(s"Failed to write git.properties file: ${ex.getMessage}")
        } finally {
          writer.close()
        }
      }

      def getGitOutput(command: String): Option[String] = {
        Try(sys.process.Process(command).lineStream_!.headOption)
          .recover {
            case ex: IOException =>
              println(s"Failed to execute Git command: $command")
              None
          }
          .getOrElse(None)
      }
    }
  }
