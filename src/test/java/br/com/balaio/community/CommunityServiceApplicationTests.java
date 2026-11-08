package br.com.balaio.community;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:community-test",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "balaio.worker.enabled=false"
})
class CommunityServiceApplicationTests {
	@MockitoBean JwtDecoder decoder;

	@Test
	void contextLoads() {
	}

}
