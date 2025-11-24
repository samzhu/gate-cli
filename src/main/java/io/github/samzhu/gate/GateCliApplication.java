package io.github.samzhu.gate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.shell.command.annotation.CommandScan;
import org.springframework.web.client.RestClient;

/**
 * Gate-CLI: Simplify Claude Code configuration with OAuth2-protected custom API endpoints.
 * Uses Spring Shell 3.x new @Command annotation model with @CommandScan.
 */
@SpringBootApplication
@CommandScan
public class GateCliApplication {

	public static void main(String[] args) {
		SpringApplication.run(GateCliApplication.class, args);
	}

	/**
	 * Provides RestClient.Builder for OAuth2Service and other HTTP operations.
	 */
	@Bean
	public RestClient.Builder restClientBuilder() {
		return RestClient.builder();
	}
}
