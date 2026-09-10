package com.oae.fakka;

import com.oae.fakka.config.AiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * {@link AiProperties} is bound here rather than annotated as a component so that it stays a
 * plain record: nothing about it needs a bean lifecycle, and a test can construct one directly.
 */
@SpringBootApplication
@EnableConfigurationProperties(AiProperties.class)
public class FakkaApplication {

	public static void main(String[] args) {
		SpringApplication.run(FakkaApplication.class, args);
	}

}
