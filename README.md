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
Swagger doc site is available at `http://localhost:8080/swagger-ui.html`
(substitute `http://localhost:8080` with any possible host you have deployed your package to.)
### Need the OpenAPI 3 yaml file?
It is available for download while running the service at `http://localhost:8080/v3/api-docs.yaml` - 
gets generated from code (specifically from Spring annotations) 
