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

package za.co.absa.loginsvc.rest.config

import org.springframework.boot.context.properties.{ConfigurationProperties, ConstructorBinding}
import javax.annotation.PostConstruct
import java.io.PrintWriter
import java.text.SimpleDateFormat

@ConstructorBinding
@ConfigurationProperties(prefix = "loginsvc.rest.config")
case class GitConfig(gitProperties: Boolean) {

  @PostConstruct
  def init(): Unit = {
    if(gitProperties)
    this.GitPropertiesGenerator.generateGitProperties()
  }

  object GitPropertiesGenerator {
    def generateGitProperties(): Unit = {
      val gitPropertiesFile = "service\\src\\main\\resources\\git.properties"
      val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
      val gitBranch = sys.process.Process("git rev-parse --abbrev-ref HEAD").lineStream_!.headOption.getOrElse("unknown")
      val gitCommitMessage = sys.process.Process("git log -1 --pretty=%B").lineStream_!.headOption.getOrElse("unknown")
      val gitCommitId = sys.process.Process("git rev-parse HEAD").lineStream_!.headOption.getOrElse("unknown")
      val gitCommitTime = dateFormat.format(sys.process.Process("git show -s --format=%ct HEAD").lineStream_!.headOption.map(_.toLong * 1000).getOrElse(0L))
      val gitAuthor = sys.process.Process("git show -s --format='%ae'").lineStream_!.headOption.getOrElse("unknown")
      val content =
        s"""|git.branch=$gitBranch
            |git.commit.message.full=$gitCommitMessage
            |git.commit.id=$gitCommitId
            |git.commit.time=$gitCommitTime
            |git.commit.user.email=$gitAuthor
            |""".stripMargin

      val writer = new PrintWriter(gitPropertiesFile)
      writer.println(content)
      writer.close()
    }
  }

}


