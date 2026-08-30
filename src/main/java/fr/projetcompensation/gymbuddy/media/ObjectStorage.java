package fr.projetcompensation.gymbuddy.media;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

public interface ObjectStorage {

    URI signPut(String key, String mime, Duration ttl);

    URI signGet(String key, String mime, Duration ttl);

    void put(String key, String mime, byte[] body);

    Optional<byte[]> get(String key);

    boolean exists(String key);

    void delete(String key);
}
