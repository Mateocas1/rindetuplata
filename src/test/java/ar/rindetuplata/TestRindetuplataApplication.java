package ar.rindetuplata;

import org.springframework.boot.SpringApplication;

public class TestRindetuplataApplication {

	public static void main(String[] args) {
		SpringApplication.from(RindetuplataApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
