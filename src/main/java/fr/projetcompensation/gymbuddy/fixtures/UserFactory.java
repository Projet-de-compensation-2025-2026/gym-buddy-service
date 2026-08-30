package fr.projetcompensation.gymbuddy.fixtures;

import fr.projetcompensation.gymbuddy.profiles.ExperienceLevel;
import fr.projetcompensation.gymbuddy.profiles.PreferredWindow;
import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.profiles.ProfileVisibility;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRole;
import fr.projetcompensation.gymbuddy.users.UserStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import net.datafaker.Faker;

public final class UserFactory {

    public static final String ALEX_EMAIL = "demo.alex@fixtures.gym.test";
    public static final String BLAKE_EMAIL = "demo.blake@fixtures.gym.test";
    public static final String MOD_EMAIL = "demo.mod@fixtures.gym.test";
    public static final String ADMIN_EMAIL = "demo.admin@fixtures.gym.test";

    private final Faker faker;
    private final Random random;
    private final long seed;
    private final Instant origin;
    private final String bulkPasswordHash;
    private final String alexHash;
    private final String blakeHash;
    private final String modHash;
    private final String adminHash;

    public UserFactory(
            long seed,
            Instant origin,
            String bulkPasswordHash,
            String alexHash,
            String blakeHash,
            String modHash,
            String adminHash) {
        this.seed = seed;
        this.origin = origin;
        this.bulkPasswordHash = bulkPasswordHash;
        this.alexHash = alexHash;
        this.blakeHash = blakeHash;
        this.modHash = modHash;
        this.adminHash = adminHash;
        this.random = new Random(seed);
        this.faker = new Faker(Locale.ENGLISH, random);
    }

    public List<UserDraft> create(int users) {
        int total = Math.max(users, FixtureCatalog.DEMO_HANDLES.size());
        List<UserDraft> drafts = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            drafts.add(one(i));
        }
        return List.copyOf(drafts);
    }

    public UserDraft one(int index) {
        return switch (index) {
            case 0 ->
                demo(
                        0,
                        FixtureCatalog.ALEX_HANDLE,
                        ALEX_EMAIL,
                        "Alex Rivera",
                        UserRole.MEMBER,
                        alexHash,
                        "Porto runner looking for 6am partners.",
                        List.of("running", "yoga"),
                        ExperienceLevel.INTERMEDIATE);
            case 1 ->
                demo(
                        1,
                        FixtureCatalog.BLAKE_HANDLE,
                        BLAKE_EMAIL,
                        "Blake Chen",
                        UserRole.MEMBER,
                        blakeHash,
                        "Friend of Alex. Same cluster so suggestions work.",
                        List.of("running"),
                        ExperienceLevel.INTERMEDIATE);
            case 2 ->
                demo(
                        4,
                        FixtureCatalog.MOD_HANDLE,
                        MOD_EMAIL,
                        "Morgan Mod",
                        UserRole.MODERATOR,
                        modHash,
                        "Staff moderator for the local demo.",
                        List.of("cycling"),
                        ExperienceLevel.ADVANCED);
            case 3 ->
                demo(
                        2,
                        FixtureCatalog.ADMIN_HANDLE,
                        ADMIN_EMAIL,
                        "Avery Admin",
                        UserRole.ADMIN,
                        adminHash,
                        "Staff admin. Triggers fixtures from the back-office.",
                        List.of("weightlifting"),
                        ExperienceLevel.ADVANCED);
            default -> bulk(index);
        };
    }

    private UserDraft demo(
            int clusterIndex,
            String handle,
            String email,
            String displayName,
            UserRole role,
            String passwordHash,
            String bio,
            List<String> sports,
            ExperienceLevel experience) {
        FixtureCatalog.Cluster cluster = FixtureCatalog.cluster(clusterIndex);
        UUID id = FixtureIds.demo(handle);
        Instant created = origin.plusSeconds(clusterIndex);
        User user = new User(id, email, handle, passwordHash, role, UserStatus.ACTIVE, created);
        Profile profile = new Profile(
                id,
                displayName,
                bio,
                ProfileVisibility.PUBLIC,
                sports,
                experience,
                cluster.city(),
                cluster.lat(),
                cluster.lng(),
                List.of(new PreferredWindow(1, "07:00", "09:00"), new PreferredWindow(3, "18:00", "20:00")),
                null);
        return new UserDraft(user, profile, clusterIndex);
    }

    private UserDraft bulk(int index) {
        FixtureCatalog.Cluster cluster = FixtureCatalog.cluster(index);
        UUID id = FixtureIds.of(seed, "user", index);
        String handle = "u%05d".formatted(index);
        String email = handle + "@fixtures.gym.test";
        Instant created = origin.plusSeconds(index);
        String displayName = faker.name().fullName();
        String bio = faker.lorem().sentence(8);
        ExperienceLevel experience = ExperienceLevel.values()[index % ExperienceLevel.values().length];
        ProfileVisibility visibility = index % 11 == 0 ? ProfileVisibility.PRIVATE : ProfileVisibility.PUBLIC;
        double jitterLat = cluster.lat() + (random.nextDouble() - 0.5) * 0.04;
        double jitterLng = cluster.lng() + (random.nextDouble() - 0.5) * 0.04;
        User user = new User(id, email, handle, bulkPasswordHash, UserRole.MEMBER, UserStatus.ACTIVE, created);
        Profile profile = new Profile(
                id,
                displayName,
                bio,
                visibility,
                List.of(cluster.sport()),
                experience,
                cluster.city(),
                jitterLat,
                jitterLng,
                List.of(),
                null);
        return new UserDraft(user, profile, index);
    }
}
