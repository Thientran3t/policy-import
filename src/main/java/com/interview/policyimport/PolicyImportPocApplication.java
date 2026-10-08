package com.interview.policyimport;

import com.interview.policyimport.config.PartnerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(PartnerProperties.class)
public class PolicyImportPocApplication {

	public static void main(String[] args) {
		SpringApplication.run(PolicyImportPocApplication.class, args);
	}

}
