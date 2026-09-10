package com.oae.fakka;

import com.oae.fakka.config.AiProperties;
import com.oae.fakka.config.OcrProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * {@link AiProperties} and {@link OcrProperties} are bound here rather than annotated as
 * components so that they stay plain records: nothing about them needs a bean lifecycle, and a
 * test can construct either directly.
 */
@SpringBootApplication
@EnableConfigurationProperties({AiProperties.class, OcrProperties.class})
public class FakkaApplication {

	public static void main(String[] args) {
		SpringApplication.run(FakkaApplication.class, args);
	}

}
