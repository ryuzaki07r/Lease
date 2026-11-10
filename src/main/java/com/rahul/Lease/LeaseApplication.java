package com.rahul.Lease;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LeaseApplication {

	public static void main(String[] args) {
		SpringApplication.run(LeaseApplication.class, args);
	}

}
