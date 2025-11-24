package io.github.samzhu.gate.command.availability;

import io.github.samzhu.gate.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.shell.Availability;
import org.springframework.shell.AvailabilityProvider;
import org.springframework.stereotype.Component;

/**
 * Availability provider that checks if a connection is active.
 * Used to enable/disable commands that require an active connection.
 */
@Component
@RequiredArgsConstructor
public class ConnectedAvailability implements AvailabilityProvider {

    private final ConfigurationService configurationService;

    @Override
    public Availability get() {
        if (configurationService.isConnected()) {
            return Availability.available();
        }
        return Availability.unavailable("you are not connected. Use 'connect' command first.");
    }
}
