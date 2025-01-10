package za.co.absa.loginsvc.rest.model

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode

case class PublicKeySet(
  @JsonProperty("keys")
  @Schema(requiredMode = RequiredMode.REQUIRED,
    description = "The full set public keys including the one currently signing JWTs",
    example = """[
      {"key": "ABCDEFGH1234"},
      {"key": "ZYXWVUT9876"},
    ]""")
  keys: List[PublicKey]
) extends AnyVal
