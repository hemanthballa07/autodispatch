package com.autodispatch;

import org.springframework.boot.SpringApplication;

public class TestAutodispatchApplication {

	public static void main(String[] args) {
		SpringApplication.from(AutodispatchApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
