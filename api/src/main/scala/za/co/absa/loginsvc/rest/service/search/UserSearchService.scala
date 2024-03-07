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

package za.co.absa.loginsvc.rest.service.search

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import za.co.absa.loginsvc.model.User

@Service
class UserSearchService @Autowired()(userRepositories: UserRepositories) {
  def searchUser(username: String): User = {
    val maybeFoundUser = userRepositories.orderedProviders.toIterator // this makes it lazy
      .map(_.searchForUser(username)) // expensive operation done lazily
      .dropWhile(_.isEmpty) // keeps searching until the possibly-found-user is not empty or all providers are exhausted

    if (maybeFoundUser.hasNext) {
      maybeFoundUser.next().getOrElse(throw new IllegalStateException(s"Valid user $maybeFoundUser should be available"))
    } else
      throw new NoSuchElementException(s"No user found by username $username anywhere")
  }
}
