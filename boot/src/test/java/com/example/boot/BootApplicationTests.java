package com.example.boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootTest
@ComponentScan(
	basePackages = "com.example.boot",
	excludeFilters = @ComponentScan.Filter(
		type = FilterType.REGEX,
		pattern = "com.example.boot.exchange_layer.*"
	)
)
class BootApplicationTests {

	@Test
	void contextLoads() {
	}

}
