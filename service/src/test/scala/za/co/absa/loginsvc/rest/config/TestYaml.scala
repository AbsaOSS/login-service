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

object TestYaml {
  def testString : String =
    "loginsvc:\r\n" +
      "  rest:\r\n" +
      "    jwt:\r\n" +
      "      exp-time: 4\r\n" +
      "      alg-name: \"RS256\"\r\n" +
      "    config:\r\n" +
      "      some-key: \"BETA\"\r\n\r\n" +
      "    auth:\r\n" +
      "      provider:\r\n" +
      "        ldap:\r\n" +
      "          order: 1\r\n" +
      "          domain: \"some.domain.com\"\r\n" +
      "          url: \"ldaps://some.domain.com:636/\"\r\n" +
      "          search-filter: \"(samaccountname={1})\"\r\n" +
      "        users:\r\n" +
      "          order: 0\r\n" +
      "          known-users:\r\n" +
      "            - username: \"user1\"\r\n" +
      "              password: \"password1\"\r\n" +
      "              groups:\r\n" +
      "                - \"group1\"\r\n\r\n" +
      "spring:\r\n" +
      "  application:\r\n" +
      "    name: \"login-service\"\r\n" +
      "  jmx:\r\n" +
      "    enabled: true\r\n" +
      "server:\r\n" +
      "  port: 9090\r\n\r\n" +
      "springdoc:\r\n" +
      "  show-actuator: true\r\n\r\n" +
      "management:\r\n" +
      "  health:\r\n" +
      "    ldap:\r\n" +
      "      enabled: \"false\"\r\n" +
      "  info:\r\n" +
      "    env:\r\n" +
      "      enabled: \"true\"\r\n" +
      "  endpoints:\r\n" +
      "    jmx:\r\n" +
      "      exposure:\r\n" +
      "        include:\r\n" +
      "          - \"health\"\r\n" +
      "          - \"info\"\r\n" +
      "    web:\r\n" +
      "      exposure:\r\n" +
      "        include:\r\n" +
      "          - \"health\"\r\n" +
      "          - \"info\"\r\n" +
      "  endpoint:\r\n" +
      "    health:\r\n" +
      "      show-details: \"never\"\r\n\r\n" +
      "info:\r\n" +
      "  test: \"This is a test value\""
}
