package za.co.absa.logingw.rest.config

import org.junit.jupiter.api.Test
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(locations = Array("classpath:application.properties"))
class BaseConfigTest {

  @Autowired
  var baseConfig: BaseConfig = _

  @Test
  def testBaseConfig(): Unit = {
    baseConfig.algName shouldBe "RS256"
    baseConfig.expTime shouldBe 2
    baseConfig.someKey shouldBe "BETA"
  }


}
