# login-gateway
AbsaOSS Common Login gateway using JWT Public key signatures

## Basic usecase schematics
![login-gw-basic-usecase2](https://user-images.githubusercontent.com/4457378/219037599-5674b63b-403c-4c02-8a54-a6e12dc01d47.png)

## Configuration
The project requires a valid configuration file to run.
An [example configuration](https://github.com/AbsaOSS/login-gateway/blob/master/service/src/main/resources/example.application.yaml)
file is provided to take inspiration from.

The project will look for the Spring config in multiple places and
in a specific order precisely as described at
[Spring boot - Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config).
Without having to study the whole documentation section, let us offer a few simple ways:

### Locally available `application.yaml`
If you are looking to build and run locally, just copy and rename the `example.application.yaml` to
`application.yaml` (and make any changes to the example data) and you will be up and running.
This will package the `application.yaml` to the resulting package, so this approach is only usage for local tests/development.

### Supply argument `--spring.config.location=my/path/my.application.yaml`
Externally-defined-`application.yaml` option that will not package (i.e. pollute) the resulting package.

Shown in IDEA:

![spring-config-location-idea-example-censored](https://github.com/AbsaOSS/login-gateway/assets/4457378/02390dbe-0b71-48e3-a3ea-b6ca7f6ea500)


## Source idea
The implementation is heavily inspired by [Enceladus](https://github.com/AbsaOSS/enceladus) that already contained
a similar single purpose login functionality with JWT. Also, Tomcat@SBT implementation is on the other hand drawn from
[atum-service](https://github.com/AbsaOSS/atum-service).


## Current development state
Currently, only a skeleton of the project exists. The project uses `xsbt-web-plugin` plugin, therefore to get
the service running (also builds the service `war`), one can run:

```sbt
sbt
service / Tomcat / start
```

## API documentation:
Swagger doc site is available at `http://localhost:port/swagger-ui.html`
(substitute `http://localhost:port` with any possible host and port you have deployed your package to.)
### Need the OpenAPI 3 yaml file?
It is available for download while running the service at `http://localhost:port/v3/api-docs.yaml` -
gets generated from code (specifically from Spring annotations)

## Authentication Providers
### Enabling and Selecting Authentication Providers
The Login Service allows users to select which authentication providers they would like to use
as well as the order in which the authentication methods get prioritized.

This is done by setting a number (Greater than 0) on the "enable" tag for each auth method.
the number used indicates the order in which you would like to use the method.

For Example:
The 2 methods currently enabled are config specified users:
```
logingw.rest.users.enable = 1
```
and ldap:
```
logingw.rest.auth.ad.ldap = 2
```
In the above example, both methods are enabled with the config specified users taking priority over Ldap.

In order to disable an authentication protocol, set the `enable` property to `0`.
Please ensure at least one auth method is enabled.

### ActiveDirectoryLDAPAuthenticationProvider
Uses LDAP(s) to authenticate user in Active Directory and to fetch groups that this user belongs to.

Requires `ActiveDirectoryLDAPConfig(domain: String, url: String, searchFilter: String)`.
#### Tips
##### How to obtain certificate for LDAPS
1. Run `openssl s_client -connect <ldaps_host>:<ldaps_port>`.
2. Copy the part starting with `-----BEGIN CERTIFICATE-----` and ending with `-----END CERTIFICATE-----`.
3. Create file `ldapcert.pem` and paste content from (2) there.
4. Import the certificate to `cacarts`:
   - For JDK8: Run `keytool -import -file ldapcert.pem -alias ldaps -keystore <path_to_jdk>/jre/lib/security/cacerts -storepass <password>` (default password is *changeit*).
   - For JDK11: Run `keytool -import -file ldapcert.pem -alias ldaps -keystore <path_to_jdk>/lib/security/cacerts -storepass <password>` (default password is *changeit*).
5. Enter `yes` when prompted.

## How to generate Code coverage report
```
sbt jacoco
```
Code coverage will be generated on path:
```
{project-root}/{module}/target/scala-{scala_version}/jacoco/report/html
```
## Health check endpoint
Springboot Actuator is enabled for this project. This provides the user with an endpoint (readable via HTTP or JMX)
that describes the overall status of the login-gateway as well as its parts.
### Accessing health endpoint via http
Health Endpoint can be accessed via http using the following URL: `http://localhost:port/actuator/health`
Accessing the above should give you the following Json message if all is functional and healthy:
```
    {"status":"UP"}
```
If one of the monitored dependencies are unavailable or unhealthy then you will get:
```
    {"status":"DOWN"}
```
If you wish for a full breakdown of the applications health including all dependencies then add the following to the application.properties file:
```
    management.endpoint.health.show-details=always
```
The health endpoint is also available via the Swagger: `http://localhost:port/swagger-ui.html`
### Using JMX to monitor /actuator/health
Local JMX is currently enabled on the project. In order to utilize it please follow the following steps:
#### Steps
1. Start the login-gateway application.
2. Open a terminal or command prompt and run the following command to start JConsole:
    ```
    jconsole
    ```
3. In the JConsole window that opens, select the process corresponding to the login-gateway application from the list of local processes.
4. Click the Connect button. JConsole will connect to the JMX agent running in the login-gateway application.
5. In the MBean tab, expand the org.springframework.boot domain to see the available JMX endpoints.
6. Find the health endpoint and click on it to view its attributes and operations. You can use the attributes and operations to check the health of the login-gateway and perform management tasks.
   You can now use JConsole to monitor and manage your local application by accessing the available endpoints.
   Remote JMX is also an option and may be enabled with some config changes in the application.properties file.

## Info endpoint
Springboot Actuator is enabled for this project. This enables an Info endpoint that can be populated with various information that may be
useful to the troubleshooting and usage of the application. The endpoint can be accessed via the following url:  `http://localhost:port/actuator/info`.
The information types available and how to use them is shown in the example config (`example.application.yaml`).

Running the example config will get you the following output:

 ```
{"app":{"name":"login-gateway","build":"0.1","description":"Application used a reusable authentication tool","env":"Dev"},"security":{"ldap":"Enabled"},"git":{"commit":{"id":{"full":"git_id"},"message":{"full":"Added Git Properties"},"user":{"email":"exampleuser@org.com"},"time":"5/15/2023"}}}
 ```

If you wish to change what is shown here, you can do so by changing the fields and attributes in the application file.
More info on the Actuator Info Service can be found here: https://reflectoring.io/spring-boot-info-endpoint/

## git.properties
An example git.properties file has also been included (`example.git.properties`), simply rename it to `git.properties` in order to make use of it in the info endpoint.
If you wish to generate an accurate `git.properties file`, you can do so in 2 ways:

1) Setting the `logingw.rest.config.git-info.generate-git-properties` to `true` will display newly generated git information.
   Additionally setting `logingw.rest.config.git-info.generate-git-properties-file` to `true` will generate a new file with the updated git information on application startup.
2) By manually adjusting and running the test in `za.co.absa.logingw.rest.actuator.tooling.GitPropertiesGenerator.scala`
   In order to run the test the line (line 30) that reads as:
   ```
   ignore should "generate git.properties file" in {
   ```
   should be changed to:
   ```
   "This function" should "generate git.properties file" in {
   ```
   once this is done, running or debugging the test will generate a `git.properties` file to be used for the info endpoint.

This requires Git to be installed and available on the host.
The example `git.properties` file provided may be edited manually if the git generation is functioning incorrectly.