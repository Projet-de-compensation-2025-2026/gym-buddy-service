package fr.projetcompensation.gymbuddy.config;

import fr.projetcompensation.gymbuddy.health.ObjectStorageHealthPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(ObjectStorageHealthPort.class)
public class UnavailableObjectStorageHealthAdapter implements ObjectStorageHealthPort {

    @Override
    public boolean reachable() {
        return false;
    }

    @Override
    public String detail() {
        return "not configured";
    }
}
