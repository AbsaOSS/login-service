[![Maven Central](https://maven-badges.herokuapp.com/maven-central/za.co.absa/login-service-client-library_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/za.co.absa/login-service-client-library_2.12/)

# Login-service client library

This library provides client-functionality for the login-service.

## Usage

Include the library in your project:
### Maven

`<dependency>
    <groupId>za.co.absa</groupId>
    <artifactId>login-service-client-library_2.12</artifactId>
    <version>1.0.0</version>
</dependency>`

### SBT

`libraryDependencies += "za.co.absa" % "login-service-client-library_2.12" % "1.0.0"`

See the [examples](examples)
for a more detailed view of how to use the library.

## Public key retrieval

The library provides a `PublicKeyRetrievalClient` class that can be used to retrieve the public key to verify tokens' signatures.
Public Key is available without authorization so just the relevant host needs to be provided. Public Key is available as a `String` and as a JWKS.

## Token retrieval

The library provides a `TokenRetrievalClient` class that can be used to retrieve access and refresh tokens.
Refresh and Access Keys require authorization. Basic Auth is used for the initial retrieval so a valid username and password is required.
Please see the [login-service documentation](README.md) for more information on what a valid username and password is.
Refresh token from initial retrieval is used to refresh the access token.

## Creating and Using a JWT Decoder

The User can create and use the `org.springframework.security.oauth2.jwt.NimbusJwtDecoder` by utilizing the 'JwtDecoderProvider' object.
This allows the user to create the decoder from a publicKey object, String or URL.

## Parsing and using Claims

`AccessTokenClaimsParser` object is used to parse decoded Access Token claims.
`RefreshTokenClaimsParser` object is used to parse decoded Refresh Token claims.
Both are used to extract the claims from the respective decoded jwt which can be used to check and verify the token claims.

For example, one may check an access token for the `groups` claim to indicate what a user may or may not do.

## Token Verification

The TokenVerifiers are used to verify if a token is valid.
The `AccessTokenVerifier` is used to verify an access token.
The `RefreshTokenVerifier` is used to verify a refresh token.

These verifiers check if the token has the following:
1. A valid signature
2. The token is not expired
3. The token is of the correct type

It will Return a JWT Object with claims that can be read if the token is valid.

## Example Code

An example of how to use the library can be found in the [examples](examples) folder.
The example makes use of a [configuration file](examples/src/main/resources/example.application.yaml) to provide the necessary configuration to the library.

Configurations required are:
1. `host` - the url of the login-service (Including Port if required)
2. 'refresh-period' - the period between refreshing the public-key used for verification. This Parameter is optional.