package io.github.samzhu.gate;

import io.github.samzhu.gate.model.ClaudeSettings;
import io.github.samzhu.gate.model.ConnectionConfig;
import io.github.samzhu.gate.model.OAuth2TokenResponse;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.shell.command.annotation.CommandScan;
import org.springframework.web.client.RestClient;

/**
 * Gate-CLI: Simplify Claude Code configuration with OAuth2-protected custom API endpoints.
 * Uses Spring Shell 3.x new @Command annotation model with @CommandScan.
 *
 * @RegisterReflectionForBinding registers model classes for Jackson deserialization
 * in GraalVM native image. Without this, Jackson cannot construct instances at runtime.
 * See: https://stackoverflow.com/questions/74908739/spring-boot-3-native-image-with-jackson
 */
@SpringBootApplication
@CommandScan
@RegisterReflectionForBinding({
    OAuth2TokenResponse.class,
    ConnectionConfig.class,
    ConnectionConfig.CurrentConnection.class,
    ConnectionConfig.BackupSettings.class,
    ClaudeSettings.class
})
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
