# login-gateway
AbsaOSS Common Login gateway using JWT Public key signatures

## Basic usecase schematics
![login-gw-basic-usecase2](https://user-images.githubusercontent.com/4457378/219037599-5674b63b-403c-4c02-8a54-a6e12dc01d47.png)


## Necessary preconditions
  - TODO configuration description

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
### ActiveDirectoryLDAPAuthenticationProvider
Uses LDAP(s) to authenticate user in Active Directory and to fetch groups that this user belongs to.

Requires `ActiveDirectoryLDAPConfig(domain: String, url: String, searchFilter: String)`.
#### Tips
##### How to obtain certificate for LDAPS
1. Run `openssl s_client -connect <ldaps_host>:<ldaps_port>`.
2. Copy the part starting with `-----BEGIN CERTIFICATE-----` and ending with `-----END CERTIFICATE-----`. 
3. Create file `ldapcert.pem` and paste content from (2) there.
4. Run `keytool -import -file ldapcert.pem -alias ldaps -keystore <path_to_jdk>/jre/lib/security/cacerts -storepass <password>` (default password is *changeit*).
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