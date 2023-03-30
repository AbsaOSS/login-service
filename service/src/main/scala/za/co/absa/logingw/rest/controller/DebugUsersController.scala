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

package za.co.absa.logingw.rest.controller

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, MediaType}
import org.springframework.web.bind.annotation._
import za.co.absa.logingw.rest.config.UsersConfig

import java.util.concurrent.CompletableFuture
import scala.concurrent.Future

//TODO remove in the end, just for dev/test
@RestController
@RequestMapping(Array("/users"))
class DebugUsersController @Autowired()(usersConf: UsersConfig) {

  import za.co.absa.logingw.utils.implicits._

  val logger = LoggerFactory.getLogger(classOf[DebugUsersController])


  @GetMapping(path = Array(""), produces = Array(MediaType.TEXT_PLAIN_VALUE))
  @ResponseStatus(HttpStatus.OK)
  def getUsersFromConfigDebugListing(): CompletableFuture[String] = {
    logger.info(s"Debug knownUsers:\n${usersConf.knownUsers.mkString(",\n")}")

    Future.successful(s"Debug knownUsers:\n${usersConf.knownUsers.mkString(",\n")}")
  }

}
