package com.sameerahmed.projects.airBnbApp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full context load requires a reachable DB matching entity mappings (PostgreSQL arrays).
 * Prefer unit / {@code @WebMvcTest} suites for CI without Postgres.
 */
@SpringBootTest
@org.junit.jupiter.api.Disabled("Requires PostgreSQL; covered by unit and WebMvc tests")
class AirBnbAppApplicationTests {

	@Test
	void contextLoads() {
	}

}
