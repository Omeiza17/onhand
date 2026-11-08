package dev.codingstoic.onhand;

import org.springframework.boot.SpringApplication;

public class TestOnhandApplication {

	public static void main(String[] args) {
		SpringApplication.from(OnhandApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
