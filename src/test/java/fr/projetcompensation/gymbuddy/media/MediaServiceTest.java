package fr.projetcompensation.gymbuddy.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.ErrorCode;
import fr.projetcompensation.gymbuddy.profiles.FriendshipQueries;
import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.profiles.ProfileVisibility;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import fr.projetcompensation.gymbuddy.users.UserRole;
import fr.projetcompensation.gymbuddy.users.UserStatus;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MediaServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    private InMemoryUsers users;
    private InMemoryProfiles profiles;
    private InMemoryFriends friends;
    private InMemoryMedia media;
    private InMemoryStorage storage;
    private MutableClock clock;
    private MediaService service;
    private User alex;
    private User blake;
    private User casey;
    private User mod;

    @BeforeEach
    void setUp() {
        users = new InMemoryUsers();
        profiles = new InMemoryProfiles();
        friends = new InMemoryFriends();
        media = new InMemoryMedia();
        storage = new InMemoryStorage();
        clock = new MutableClock(NOW);
        service = new MediaService(media, storage, users, profiles, friends, clock);
        alex = member("alex");
        blake = member("blake");
        casey = member("casey");
        mod = member("mod", UserRole.MODERATOR);
        profiles.save(privateProfile(alex));
        profiles.save(Profile.created(blake.id(), "blake"));
        profiles.save(Profile.created(casey.id(), "casey"));
        profiles.save(Profile.created(mod.id(), "mod"));
        friends.accept(alex.id(), blake.id());
    }

    @Test
    void fsMed05_quotaExceededWhenUserAlreadyAt1GiB() {
        media.save(new Media(
                UUID.randomUUID(),
                alex.id(),
                MediaKind.AVATAR,
                "image/jpeg",
                MediaRules.QUOTA_BYTES,
                0,
                MediaStatus.READY,
                "original/" + alex.id() + "/full",
                NOW,
                null));

        assertThatThrownBy(() -> service.create(alex.id(), "avatar", "image/jpeg", 240000))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.QUOTA_EXCEEDED));
    }

    @Test
    void fsMed03_payloadTooLargeOver8MiB() {
        assertThatThrownBy(() -> service.create(alex.id(), "avatar", "image/jpeg", MediaRules.MAX_FILE_BYTES + 1))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));
    }

    @Test
    void fsMed04_audioOnAvatarIsValidation() {
        assertThatThrownBy(() -> service.create(alex.id(), "avatar", "audio/mpeg", 2048))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.VALIDATION));
    }

    @Test
    void fsMed06_strangerCannotGetPrivateAvatarUrl() {
        Media ready = readyAvatar(alex, jpeg());

        assertThatThrownBy(() -> service.url(casey.id(), ready.id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThat(service.url(blake.id(), ready.id()).url().toString()).contains(ready.objectKey());
        assertThat(service.url(alex.id(), ready.id()).url().toString()).contains(ready.objectKey());
        assertThat(service.url(mod.id(), ready.id()).url().toString()).contains(ready.objectKey());
    }

    @Test
    void fsMed06_strangerCannotGetFriendsOnlyPostUrl() {
        CreateUpload upload = service.create(alex.id(), "post", "image/jpeg", jpeg().length);
        storage.put(media.findById(upload.mediaId()).orElseThrow().objectKey(), "image/jpeg", jpeg());
        service.sweep();
        Media ready = media.findById(upload.mediaId()).orElseThrow();
        assertThat(ready.status()).isEqualTo(MediaStatus.READY);

        assertThatThrownBy(() -> service.url(casey.id(), ready.id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThat(service.url(alex.id(), ready.id()).url()).isNotNull();
    }

    @Test
    void fsMed06_publicProfileAvatarIsReadableByStranger() {
        profiles.save(new Profile(
                casey.id(),
                "Casey",
                null,
                ProfileVisibility.PUBLIC,
                List.of(),
                null,
                null,
                null,
                null,
                List.of(),
                null));
        Media ready = readyAvatar(casey, jpeg());
        assertThat(service.url(alex.id(), ready.id()).url().toString()).contains(ready.objectKey());
    }

    @Test
    void fsMed_magicByteMismatchIsRejectedAndUrlIsNotFound() {
        CreateUpload upload = service.create(alex.id(), "avatar", "image/jpeg", 12);
        Media pending = media.findById(upload.mediaId()).orElseThrow();
        storage.put(pending.objectKey(), "image/jpeg", "not-a-jpeg!!!!".getBytes());
        service.sweep();

        Media rejected = media.findById(pending.id()).orElseThrow();
        assertThat(rejected.status()).isEqualTo(MediaStatus.REJECTED);
        assertThatThrownBy(() -> service.url(alex.id(), rejected.id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void fsMed07_readyImageGetsSmAndMdWebpVariants() {
        byte[] jpeg = jpeg();
        CreateUpload upload = service.create(alex.id(), "avatar", "image/jpeg", jpeg.length);
        Media pending = media.findById(upload.mediaId()).orElseThrow();
        storage.put(pending.objectKey(), "image/jpeg", jpeg);
        service.sweep();

        Media ready = media.findById(pending.id()).orElseThrow();
        assertThat(ready.status()).isEqualTo(MediaStatus.READY);
        assertThat(ready.variantBytes()).isPositive();
        byte[] sm = storage.get(ready.variantKey("sm")).orElseThrow();
        byte[] md = storage.get(ready.variantKey("md")).orElseThrow();
        assertThat(MagicBytes.matches(sm, "image/webp")).isTrue();
        assertThat(MagicBytes.matches(md, "image/webp")).isTrue();
        assertThat(service.url(alex.id(), ready.id()).url().toString()).contains(ready.objectKey());
    }

    @Test
    void fsMed08_softDeletedMediaHidesUrlThenGraceDeletesObjects() {
        Media ready = readyAvatar(alex, jpeg());
        service.delete(alex.id(), ready.id());
        assertThatThrownBy(() -> service.url(alex.id(), ready.id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> service.delete(casey.id(), ready.id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));

        clock.set(NOW.plus(MediaService.OBJECT_GRACE).plusSeconds(1));
        service.sweep();
        assertThat(media.findById(ready.id())).isEmpty();
        assertThat(storage.get(ready.objectKey())).isEmpty();
    }

    @Test
    void fsMed_orphanPendingOlderThanOneHourIsDeleted() {
        CreateUpload upload = service.create(alex.id(), "avatar", "image/jpeg", 100);
        clock.set(NOW.plus(MediaService.PENDING_ORPHAN).plusSeconds(1));
        service.sweep();
        assertThat(media.findById(upload.mediaId())).isEmpty();
    }

    @Test
    void pendingMediaDoesNotMintUrl() {
        CreateUpload upload = service.create(alex.id(), "avatar", "image/jpeg", 100);
        assertThat(upload.uploadUrl()).isNotNull();
        assertThatThrownBy(() -> service.url(alex.id(), upload.mediaId()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    private Media readyAvatar(User owner, byte[] jpeg) {
        CreateUpload upload = service.create(owner.id(), "avatar", "image/jpeg", jpeg.length);
        Media pending = media.findById(upload.mediaId()).orElseThrow();
        storage.put(pending.objectKey(), "image/jpeg", jpeg);
        service.sweep();
        return media.findById(pending.id()).orElseThrow();
    }

    private User member(String handle) {
        return member(handle, UserRole.MEMBER);
    }

    private User member(String handle, UserRole role) {
        User user = new User(UUID.randomUUID(), handle + "@example.com", handle, "hash", role, UserStatus.ACTIVE, NOW);
        users.save(user);
        return user;
    }

    private static Profile privateProfile(User user) {
        return new Profile(
                user.id(),
                user.handle(),
                "private bio",
                ProfileVisibility.PRIVATE,
                List.of("running"),
                null,
                "Porto",
                null,
                null,
                List.of(),
                null);
    }

    private static byte[] jpeg() {
        BufferedImage image = new BufferedImage(48, 32, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.CYAN);
        graphics.fillRect(0, 0, 48, 32);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, "jpeg", out)) {
                throw new IllegalStateException("jpeg writer missing");
            }
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        return out.toByteArray();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class InMemoryStorage implements ObjectStorage {
        private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

        @Override
        public URI signPut(String key, String mime, Duration ttl) {
            return URI.create("https://minio.test/put/" + key);
        }

        @Override
        public URI signGet(String key, String mime, Duration ttl) {
            return URI.create("https://minio.test/get/" + key);
        }

        @Override
        public void put(String key, String mime, byte[] body) {
            objects.put(key, body);
        }

        @Override
        public Optional<byte[]> get(String key) {
            return Optional.ofNullable(objects.get(key));
        }

        @Override
        public boolean exists(String key) {
            return objects.containsKey(key);
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }
    }

    private static final class InMemoryMedia implements MediaRepository {
        private final Map<UUID, Media> store = new LinkedHashMap<>();

        @Override
        public void save(Media row) {
            store.put(row.id(), row);
        }

        @Override
        public void update(Media row) {
            store.put(row.id(), row);
        }

        @Override
        public void delete(UUID id) {
            store.remove(id);
        }

        @Override
        public Optional<Media> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public long usedBytes(UUID ownerId) {
            return store.values().stream()
                    .filter(row -> row.ownerId().equals(ownerId) && row.deletedAt() == null)
                    .mapToLong(row -> row.bytes() + row.variantBytes())
                    .sum();
        }

        @Override
        public List<Media> findPendingCreatedBefore(Instant cutoff) {
            return store.values().stream()
                    .filter(Media::pending)
                    .filter(row -> row.createdAt().isBefore(cutoff))
                    .toList();
        }

        @Override
        public List<Media> findPending() {
            return store.values().stream().filter(Media::pending).toList();
        }

        @Override
        public List<Media> findDeletedBefore(Instant cutoff) {
            return store.values().stream()
                    .filter(row -> row.status() == MediaStatus.DELETED
                            && row.deletedAt() != null
                            && row.deletedAt().isBefore(cutoff))
                    .toList();
        }
    }

    private static final class InMemoryUsers implements UserRepository {
        private final Map<UUID, User> store = new LinkedHashMap<>();

        @Override
        public Optional<User> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return store.values().stream()
                    .filter(user -> user.email().equalsIgnoreCase(email))
                    .findFirst();
        }

        @Override
        public Optional<User> findByHandle(String handle) {
            return store.values().stream()
                    .filter(user -> user.handle().equalsIgnoreCase(handle))
                    .findFirst();
        }

        @Override
        public long count() {
            return store.size();
        }

        @Override
        public void save(User user) {
            store.put(user.id(), user);
        }

        @Override
        public void update(User user) {
            store.put(user.id(), user);
        }
    }

    private static final class InMemoryProfiles implements ProfileRepository {
        private final Map<UUID, Profile> store = new LinkedHashMap<>();

        @Override
        public void save(Profile profile) {
            store.put(profile.userId(), profile);
        }

        @Override
        public void update(Profile profile) {
            store.put(profile.userId(), profile);
        }

        @Override
        public Optional<Profile> findByUserId(UUID userId) {
            return Optional.ofNullable(store.get(userId));
        }
    }

    private static final class InMemoryFriends implements FriendshipQueries {
        private final Map<UUID, Set<UUID>> accepted = new LinkedHashMap<>();

        void accept(UUID left, UUID right) {
            accepted.computeIfAbsent(left, id -> ConcurrentHashMap.newKeySet()).add(right);
            accepted.computeIfAbsent(right, id -> ConcurrentHashMap.newKeySet()).add(left);
        }

        @Override
        public boolean areAcceptedFriends(UUID left, UUID right) {
            return accepted.getOrDefault(left, Set.of()).contains(right);
        }

        @Override
        public int acceptedCount(UUID userId) {
            return accepted.getOrDefault(userId, Set.of()).size();
        }
    }
}
