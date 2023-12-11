# Login-service client library

This library provides client-functionality for the login-service.

## Usage

Include the library in your project:
### Maven

`<dependency>
<groupId>za.co.absa</groupId>
<artifactId>login-service-client-library_2.12</artifactId>
<version>0.1.0-SNAPSHOT</version>
</dependency>`

### SBT

`libraryDependencies += "za.co.absa" % "login-service-client-library_2.12" % "0.1.0-SNAPSHOT"`

See the [examples](examples)
for a more detailed view of how to use the library.

## Public key retrieval

The library provides a `RetrievePublicKey` class that can be used to retrieve the public key used to sign the tokens.
Public Key is available without authorization so just the relevant host needs to be provided. Public Key is available as a `String` and as a JWKS.


## Token retrieval

The library provides a `RetrieveToken` class that can be used to retrieve a JWT tokens.
Public Key is available without authorization so just the relevant host needs to be provided. Public Key is available as a `String` and as a JWKS.
Refresh and Access Keys require authorization. Basic Auth is used for the initial retrieval so a valid username and password is required.
Please see the [login-service documentation](README.md) for more information on what a valid username and password is.
Refresh token from initial retrieval is used to refresh the access token.

## Creating and Using a JWT Decoder

The library provides a `JwtDecoder` class that can be used to decode and verify JWT tokens.
Decoded Tokens are available as a `Jwt` object that provides access to the claims of the token.
The Decoder Class also allows for the verification of the token using the following parameters:
1. is valid against the public-key retrieved from the login-service
2. is not expired
3. has `type=access`

The Decoder Class also allows for periodically refreshing the public-key used for verification
if key rotation is used on the login-service.

## Parsing and using Claims

This object is used to parse Access Token claims. 
It is used to extract the claims from the token which can be used to verify the token 
and to indicate what permissions the user has.

For example, one may check what groups the user belongs to under the `groups` claim to indicate what a user may or may not do.

## Example Code

An example of how to use the library can be found in the [examples](examples) folder.
The example makes use of a [configuration file](examples/src/main/resources/example.application.yaml) to provide the necessary configuration to the library.

Configurations required are:
1. `host` - the url of the login-service (Including Port if required)
2. 'refresh-period' - the period between refreshing the public-key used for verification. This Parameter is optional.