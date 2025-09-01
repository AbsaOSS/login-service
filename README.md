# login-service
AbsaOSS Common Login service using JWT Public key signatures

## Basic use-case schematics
![login-gw-basic-usecase2](https://user-images.githubusercontent.com/4457378/219037599-5674b63b-403c-4c02-8a54-a6e12dc01d47.png)

### Usage & Integration
To interact with the service, most notable endpoints are
 - `/token/generate` to generate access & refresh tokens
 - `/token/refresh` to obtain a new access token with a still-valid refresh token
 - `/token/public-key` to obtain the currently signing public key to verify tokens including their validity window
 - `/token/public-keys` to obtain all available public keys including the current and previously rotated keys.
 - `/token/public-key-jwks` gives same data as `/token/public-keys` but in the form of a JSON Web Key Set.

Please, refer to the [API documentation](#api-documentation) below for details of the endpoints.

#### Generate tokens
Once you request your token at `/token/generate` endpoint, you will receive both an access token and a refresh token
```json
{
  "token": "...",
  "refresh": "..."
}
```
Both tokens are signed by LS public key and carry the username (`sub`), `type` (`access`/`refresh`) and creation/expiry info (`iat`/`exp`). 

#### Refresh access token
During the time the refresh token is valid, you may refresh the access token (expired or not) using the `/token/refresh` 
endpoint - as the service does not facilitate any internal service access to LDAP, both tokens must be sent. 

#### Validate access token
On the side of the integrator, in order to trust the access token, one should do the following actions:
1. obtain the public-key from LS at `/token/public-key`
2. verify that the access token
   1. is valid against this public-key (e.g. using `jwtt` library or similar)
   2. is not expired
   3. has `type=access`

## API documentation:
Swagger doc site is available at `http://localhost:port/swagger-ui.html`
(substitute `http://localhost:port` with any possible host and port you have deployed your package to.)
### Need the OpenAPI 3 yaml file?
It is available for download while running the service at `http://localhost:port/v3/api-docs.yaml` -
gets generated from code (specifically from Spring annotations)


## Configuration
The project requires a valid configuration file to run.
An [example configuration](https://github.com/AbsaOSS/login-service/blob/master/service/src/main/resources/example.application.yaml)
file is provided to take inspiration from.

The project will look for the Spring config in multiple places and
in a specific order precisely as described at 
[Spring boot - Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config).
Without having to study the whole documentation section, let us offer a few simple ways:

### Locally available `application.yaml`
If you are looking to build and run locally, just supply the following argument:
```
--spring.config.location=api/src/main/resources/example.application.yaml
```
or set the following Environment Variable:
```
"SPRING_CONFIG_LOCATION=api/src/main/resources/example.application.yaml"
```
and you will be up and running.
This will run the application using the provided example config for usage in local tests/development.

### Supply argument `--spring.config.location=my/path/my.application.yaml`
Externally-defined-`application.yaml` option that will not package (i.e. pollute) the resulting package.

Shown in IDEA:

![spring-config-location-idea-example-censored](https://github.com/AbsaOSS/login-service/assets/4457378/02390dbe-0b71-48e3-a3ea-b6ca7f6ea500)
  

## Source idea
The implementation is heavily inspired by [Enceladus](https://github.com/AbsaOSS/enceladus) that already contained 
a similar single purpose login functionality with JWT. Also, Tomcat@SBT implementation is on the other hand drawn from 
[atum-service](https://github.com/AbsaOSS/atum-service).


## Current development state
Currently, only a skeleton of the project exists. The project uses `xsbt-web-plugin` plugin, therefore to get
the service running (also builds the service `war`), one can run:

```
sbt
service / Tomcat / start
```

## Authentication Providers
### Enabling and Selecting Authentication Providers
The Login Service allows users to select which authentication providers they would like to use
as well as the order in which the authentication methods get prioritized.

This is done by setting a number (Greater than 0) on the `order` tag for each auth method.
the number used indicates the order in which you would like to use the method.

For Example:
The 2 methods currently enabled are config specified users:
```
loginsvc.rest.auth.provider.users.order = 1
```
and LDAP:
```
loginsvc.rest.auth.provider.ldap.order = 2
```
In the above example, both methods are enabled with the config specified users taking priority over Ldap.

In order to disable an authentication protocol, set the `order` property to `0`
or just exclude the properties from the config for that auth provider.
Please ensure at least one auth method is enabled.

For the service account used to search LDAP, the Service Account Name and password may be specified in the config file.
For a more secure approach, the service account name and password may be specified in AWS Secrets Manager and the application will fetch them from there.

The config also allows the user to specify additional claims to be added to the JWT token. 
These can be sourced from LDAP or specified directly in the config depending on the auth provider used.

Format of attributes list under LDAP in config is:
```
        ldap:
          # Auth Protocol
          # Set the order of the protocol starting from 1
          # Set to 0 to disable or simply exclude the LDAP tag from config
          # NOTE: At least 1 auth protocol needs to be enabled
          order: 2
          domain: "some.domain.com"
          url: "ldaps://some.domain.com:636/"
          search-filter: "(samaccountname={1})"
          service-account:
            account-pattern: "CN=%s,OU=Users,OU=CORP Accounts,DC=corp,DC=dsarena,DC=com"
            in-config-account:
                username: "svc-ldap"
                password: "password"
          attributes:
            <ldapFieldName>: "<claimName>"
```

`ldapFieldName` is the name of the field in the LDAP server and `claimName` is the name of the claim that will be added to the JWT token.

### Enabling SPNEGO authentication with LDAP
When LDAP authentication is used, there is the option of adding SPNEGO authentication via kerberos.
This will allow users to authenticate via Basic Auth or Kerberos Tickets.
The Config to enable this will look like this:
```
        ldap:
          enable-kerberos:
            krb-file-location: "/etc/krb5.conf"
            keytab-file-location: "/etc/keytab"
            spn: "HTTP/Host"
            debug: true
```
Adding the `enable-kerberos` property to the config will enable SPNEGO authentication.
In order to facilitate the kerberos authentication, you will need to provide a krb5 file that includes 
the kerberos configuration details such as domains and Kerberos distribution center address.
A Keytab file needs to be created and attached to the service. The SPN needs to match that which
is used in the keytab and matches the host of the Login Service. The `debug` property is used when
additional information is required from the logs when testing the service.

### Enable LDAP authentication retries
When LDAP authentication is used, there is the option to enable LDAP service retries.
This allows the service to retry and repeat calls to the LDAP service when the service is unreachable.
This protects the user from authentication errors when there are brief interruptions in the Ldap service or on the network.
The Config to enable this will look like this:
```
        ldap:
          ldap-retry:
            attempts: 3
            delay-ms: 100
```
Adding the `ldap-retry` property to the config will enable LDAP retry functionality.
The `attempts` property dictates the amount of retry attempts will occur before an exception is communicated to the user.
The `delay-ms` indicates the base timing between each delay. The delay for each retry is multiplied by the current attempt.
In the above example configuration, the process for failure will work as follows:
1. Initial communication to LDAP occurs and fails
2. 100ms delay
3. 1st retry attempt occurs and fails
4. 200ms delay
5. 2nd retry attempt occurs and fails
6. 300ms delay
7. 3rd retry attempt occurs and fails
8. Authentication fails and exception is communicated to user.

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

## Key Provider
The Application allows for the user to allow the application to generate a key in memory.
This is useful for single deployments and testing, however, may present issues when trying to deploy multiple login-services for redundancy.
To get around this, the application allows for you to generate your keys in AWS Secrets manager and the application will periodically fetch them.

In order to setup for in-memory key generation, your config should look like so:
```
loginsvc:
  rest:
    jwt:
      generate-in-memory:
         access-exp-time: 15min
         refresh-exp-time: 9h
         key-rotation-time: 9h
         key-lay-over-time: 15min
         key-phase-out-time: 30min
         alg-name: "RS256"
```
There are a few important configuration values to be provided:
- `access-exp-time` which indicates how long an access token is valid for,
- `refresh-exp-time` which indicates how long a refresh token is valid for,
- Optional property: `key-rotation-time` which indicates how often Key pairs are rotated. Rotation will be disabled if missing.
- Optional property: `key-lay-over-time` which indicates a delay after rotation before using the newly created key for signing. Lay-over will be disabled if missing.
- Optional property: `key-phase-out-time` which indicates the time to phase out the older key. Timer is scheduled after `key-lay-over-time` if enabled. Phase-out will be disabled if missing.
- `alg-name` which indicates which algorithm is used to encode your keys.

Using the above values, the optional properties will give the following effect after the 1st rotation at 9 hours:
```
t=0: keys rotation happens
t=0-14m: layover period: old key from before rotation is still used for signing. Both public keys available from public-keys endpoint.
t=15-44m: layover is over: new key from after rotation is used for signing. Both public keys available from public-keys endpoint.
t=45m+: phase-out happens: new key from after rotation is used for signing. Old Key is no longer available from public-keys endpoint.
```
These properties cannot be enabled if rotation is not enabled. The combined values of these properties cannot be higher than the rotation time.


To setup for AWS Secrets Manager, your config should look like so:
```
loginsvc:
  rest:
    jwt:
      aws-secrets-manager:
        secret-name: "secret"
        region: "region"
        private-key-field-name: "privateKey"
        public-key-field-name: "publicKey"
        access-exp-time: 15min
        refresh-exp-time: 9h
        poll-time: 30min
        key-lay-over-time: 15min
        key-phase-out-time: 30min
        alg-name: "RS256"
```
Your AWS Secret must have at least 2 fields which correspond to the above properties:
```
private-key-field-name: "privateKey"
public-key-field-name: "publicKey"
```
with `"privateKey"` and `"publicKey"` indicating the field-name of those secrets.
Replace the above example values with the field-names you used in AWS Secrets Manager.

There are a few important configuration values to be provided:
- `access-exp-time` which indicates how long an access token is valid for,
- `refresh-exp-time` which indicates how long a refresh token is valid for,
- Optional property:`poll-time` which indicates how often key pairs (`private-key-field-name` and `public-key-field-name`) are polled and fetched from AWS Secrets Manager. Polling will be disabled if missing.
- Optional property: `key-lay-over-time` which indicates a delay after rotation before using the newly created key for signing. Lay-over will be disabled if missing.
- Optional property: `key-phase-out-time` which indicates the time to phase out the older key. Timer is scheduled after `key-lay-over-time` if enabled. Phase-out will be disabled if missing.
- `alg-name` which indicates which algorithm is used to encode your keys.
  Using the above values, the optional properties will give the following effect after the 1st rotation at 9 hours:
```
t=0: keys rotation happens
t=0-14m: layover period: old key from before rotation is still used for signing. Both public keys available from public-keys endpoint.
t=15-44m: layover is over: new key from after rotation is used for signing. Both public keys available from public-keys endpoint.
t=45m+: phase-out happens: new key from after rotation is used for signing. Old Key is no longer available from public-keys endpoint.
```
These properties cannot be enabled if polling is not enabled.
  
Please note that only one configuration option (`loginsvc.rest.jwt.{aws-secrets-manager|generate-in-memory}`) can be used at a time.

## Generating Tokens via SPNEGO/Kerberos
To securely authenticate and retrieve a JWT token from a server using Kerberos and SPNEGO, clients (both Windows and Linux) need to be properly configured for Kerberos authentication. 
The process involves obtaining a Kerberos ticket and using it to authenticate to the endpoint.

### Steps for Windows Clients
#### 1) Kerberos Configuration:

- Ensure the Windows client is joined to the appropriate Active Directory (AD) domain.
- Verify that the Kerberos configuration is correct in the `krb5.ini` file, typically located in `C:\ProgramData\MIT\Kerberos5\` or `C:\Windows\`.
The `krb5.ini` file should include the correct realm and KDC settings. An example configuration might look like this:
```
[libdefaults]
    default_realm = YOURDOMAIN.COM
    dns_lookup_realm = false
    dns_lookup_kdc = true

[realms]
    YOURDOMAIN.COM = {
        kdc = kdc.yourdomain.com
        admin_server = kdc.yourdomain.com
    }

[domain_realm]
    .yourdomain.com = YOURDOMAIN.COM
    yourdomain.com = YOURDOMAIN.COM
```
#### 2) Optional: MIT Kerberos Installation:

- While Windows has built-in Kerberos support, you may choose to install MIT Kerberos if you need advanced features or compatibility with specific applications.
- Download the installer from the [MIT Kerberos website](https://web.mit.edu/kerberos/dist/).
- Follow the installation instructions, and ensure the krb5.ini file is properly configured as mentioned above.

#### 3) Check Kerberos Tickets:

- Use the `klist` command in the Command Prompt or use MIT Kerberos to verify the presence of a valid Kerberos ticket.
```
Credentials cache: API:1000
        Principal: user@EXAMPLE.COM
    Cache version: 5
  Ticket cache: /tmp/krb5cc_1000
  Default principal: user@EXAMPLE.COM

Valid starting       Expires              Service principal
10/20/2024 10:00:00 10/20/2024 20:00:00 krbtgt/EXAMPLE.COM@EXAMPLE.COM
10/20/2024 10:00:00 10/20/2024 20:00:00 host/server.example.com@EXAMPLE.COM
```

#### 4) Environment Setup:

- Ensure that the required libraries (e.g., SPNEGO) are available in your application or tool (e.g., Postman, Curl).

#### 5) Sending the POST Request:

- Construct a POST request to the desired endpoint.
- Example using Curl:
```
curl -i --negotiate -u : -X POST <endpoint-url>/token/generate
```

#### 6) Receive the JWT:

- On successful authentication, the server will respond with an access and refresh JWT tokens.

### Steps for Linux Clients
#### 1) Kerberos Installation:

- Install the necessary Kerberos packages (e.g., krb5-libs).

#### 2) Kerberos Configuration:

- Locate and, if necessary, replace krb5.conf. The krb5.conf file is typically located in /etc/krb5.conf.
- Ensure it includes the correct realm and KDC (Key Distribution Center) settings. A basic configuration might look like this:
```
[libdefaults]
    default_realm = YOURDOMAIN.COM
    dns_lookup_realm = false
    dns_lookup_kdc = true

[realms]
    YOURDOMAIN.COM = {
        kdc = kdc.yourdomain.com
        admin_server = kdc.yourdomain.com
    }

[domain_realm]
    .yourdomain.com = YOURDOMAIN.COM
    yourdomain.com = YOURDOMAIN.COM
```

#### 3) Obtaining a Kerberos Ticket:

- Use the following command to obtain a Kerberos ticket (A password may be required):
```
kinit username@YOURDOMAIN.COM
```

#### 4) Sending the POST Request:

- Use a tool like Curl to send a POST request:
```
curl -i --negotiate -u : -X POST <endpoint-url>/token/generate
```

#### 5) Receive the JWT:

- On successful authentication, the server will respond with an access and refresh JWT tokens.

## How to generate Code coverage report
```
sbt jacoco
```
Code coverage will be generated on path:
```
{project-root}/{module}/target/jacoco/html
```
## Health check endpoint
Springboot Actuator is enabled for this project. This provides the user with an endpoint (readable via HTTP or JMX)
that describes the overall status of the login-service as well as its parts.
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
1. Start the login-service application.
2. Open a terminal or command prompt and run the following command to start JConsole:
    ```
    jconsole
    ```
3. In the JConsole window that opens, select the process corresponding to the login-service application from the list of local processes.
4. Click the Connect button. JConsole will connect to the JMX agent running in the login-service application.
5. In the MBean tab, expand the org.springframework.boot domain to see the available JMX endpoints.
6. Find the health endpoint and click on it to view its attributes and operations. You can use the attributes and operations to check the health of the login-service and perform management tasks.
You can now use JConsole to monitor and manage your local application by accessing the available endpoints.
Remote JMX is also an option and may be enabled with some config changes in the application.properties file.

## Info endpoint
Springboot Actuator is enabled for this project. This enables an Info endpoint that can be populated with various information that may be
useful to the troubleshooting and usage of the application. The endpoint can be accessed via the following url:  `http://localhost:port/actuator/info`.
The information types available and how to use them is shown in the example config (`example.application.yaml`).

Running the example config will get you the following output:

 ```
{"app":{"name":"login-service","build":"0.1","description":"Application used a reusable authentication tool","env":"Dev"},"security":{"ldap":"Enabled"},"git":{"commit":{"id":{"full":"git_id"},"message":{"full":"Added Git Properties"},"user":{"email":"exampleuser@org.com"},"time":"5/15/2023"}}}
 ```

If you wish to change what is shown here, you can do so by changing the fields and attributes in the application file. 
More info on the Actuator Info Service can be found here: https://reflectoring.io/spring-boot-info-endpoint/

## git.properties
An example git.properties file has also been included (`example.git.properties`), simply rename it to `git.properties` in order to make use of it in the info endpoint.
If you wish to generate an accurate `git.properties file`, you can do so in 2 ways:

1) Setting the `loginsvc.rest.config.git-info.generate-git-properties` to `true` will display newly generated git information.
   Additionally setting `loginsvc.rest.config.git-info.generate-git-properties-file` to `true` will generate a new file with the updated git information on application startup.
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

## Client Library
See Readme in [clientLibrary](clientLibrary/README.md) module.
