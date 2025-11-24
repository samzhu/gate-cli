package io.github.samzhu.gate;

import org.springframework.boot.SpringApplication;

public class TestGateCliApplication {

	public static void main(String[] args) {
		SpringApplication.from(GateCliApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
