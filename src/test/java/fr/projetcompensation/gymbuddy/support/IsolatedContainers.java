package fr.projetcompensation.gymbuddy.support;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import java.util.Arrays;
import java.util.HashMap;

/** Optional resource bounds for running disposable integration tests on a shared host. */
public final class IsolatedContainers {
    private IsolatedContainers() {}

    public static void configure(CreateContainerCmd command) {
        String run = System.getenv("GYMBUDDY_ISOLATED_TEST_RUN");
        if (run == null || run.isBlank()) return;
        var labels = new HashMap<>(command.getLabels());
        labels.put("gymbuddy.audit", run);
        command.withLabels(labels);
        var ports = Arrays.stream(command.getExposedPorts())
                .map(port -> new PortBinding(Ports.Binding.bindIp("127.0.0.1"), port))
                .toList();
        command.getHostConfig()
                .withPortBindings(ports)
                .withMemory(384L * 1024 * 1024)
                .withMemorySwap(384L * 1024 * 1024)
                .withNanoCPUs(1_000_000_000L);
    }
}
