package za.co.absa.loginclient.authorization

import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.springframework.security.oauth2.jwt.JwtDecoder

import java.security.interfaces.RSAPublicKey
import java.util.Base64

class JwtDecoderProviderTest extends AnyFlatSpec with Matchers{

  "getDecoderFromPublicKeyString" should "correctly generate a JwtDecoder from a valid RSA public key string" in {
    val keyPair = Keys.keyPairFor(SignatureAlgorithm.RS256)

    val publicKey: RSAPublicKey = keyPair.getPublic.asInstanceOf[RSAPublicKey]
    val publicKeyString = Base64.getEncoder.encodeToString(publicKey.getEncoded)

    val decoder: JwtDecoder = JwtDecoderProvider.getDecoderFromPublicKeyString(publicKeyString)

    decoder should not be null
  }

}
