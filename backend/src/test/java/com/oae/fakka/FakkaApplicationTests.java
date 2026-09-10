package com.oae.fakka;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The dev profile is required: the default profile intentionally has no fallback for
 * DB_USERNAME/DB_PASSWORD, so tests run against the in-memory H2 database instead.
 */
@SpringBootTest
@ActiveProfiles("dev")
class FakkaApplicationTests {

	@Test
	void contextLoads() {
	}

}
