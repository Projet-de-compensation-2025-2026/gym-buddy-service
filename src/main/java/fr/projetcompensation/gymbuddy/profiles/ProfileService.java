package fr.projetcompensation.gymbuddy.profiles;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.FieldIssue;
import fr.projetcompensation.gymbuddy.users.DuplicateUserException;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import java.util.List;
import java.util.UUID;

public final class ProfileService {

    private static final String NOT_FOUND = "profile not found";

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final FriendshipQueries friendships;

    public ProfileService(UserRepository users, ProfileRepository profiles, FriendshipQueries friendships) {
        this.users = users;
        this.profiles = profiles;
        this.friendships = friendships;
    }

    public VisibleProfile me(UUID viewerId) {
        User owner = requireActive(viewerId);
        Profile profile = profiles.findByUserId(owner.id()).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        return VisibleProfile.full(owner, profile, friendships.acceptedCount(owner.id()));
    }

    public VisibleProfile byHandle(UUID viewerId, String handle) {
        User viewer = requireActive(viewerId);
        if (handle == null || handle.isBlank()) {
            throw AuthException.notFound(NOT_FOUND);
        }
        User owner = users.findByHandle(handle.trim()).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        if (owner.closed()) {
            throw AuthException.notFound(NOT_FOUND);
        }
        Profile profile = profiles.findByUserId(owner.id()).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        if (canViewFull(viewer, owner, profile)) {
            return VisibleProfile.full(owner, profile, friendships.acceptedCount(owner.id()));
        }
        return VisibleProfile.stub(owner, profile);
    }

    public VisibleProfile patchMe(UUID viewerId, ProfilePatch patch) {
        User owner = requireActive(viewerId);
        Profile current = profiles.findByUserId(owner.id()).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        ProfileRules.validate(patch);
        User updatedUser = owner;
        if (patch.handle() != null
                && !patch.handle().isBlank()
                && !patch.handle().equalsIgnoreCase(owner.handle())) {
            String handle = patch.handle().trim();
            users.findByHandle(handle)
                    .filter(other -> !other.id().equals(owner.id()))
                    .ifPresent(other -> {
                        throw AuthException.conflict("handle already taken", new FieldIssue("handle", "duplicate"));
                    });
            updatedUser = owner.withHandle(handle);
            try {
                users.update(updatedUser);
            } catch (DuplicateUserException ex) {
                throw AuthException.conflict("handle already taken", new FieldIssue("handle", "duplicate"));
            }
        }
        Profile updated = apply(current, patch, updatedUser.id());
        profiles.update(updated);
        return VisibleProfile.full(updatedUser, updated, friendships.acceptedCount(updatedUser.id()));
    }

    boolean canViewFull(User viewer, User owner, Profile profile) {
        if (viewer.id().equals(owner.id())) {
            return true;
        }
        if (viewer.isStaff()) {
            return true;
        }
        if (profile.visibility() == ProfileVisibility.PUBLIC) {
            return true;
        }
        return friendships.areAcceptedFriends(viewer.id(), owner.id());
    }

    private User requireActive(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> AuthException.unauthenticated("missing or invalid access token"));
        if (!user.active()) {
            throw AuthException.unauthenticated("missing or invalid access token");
        }
        return user;
    }

    private static Profile apply(Profile current, ProfilePatch patch, UUID userId) {
        String displayName = patch.displayName() != null ? patch.displayName().trim() : current.displayName();
        String bio = patch.bioSet() ? emptyToNull(patch.bio()) : current.bio();
        ProfileVisibility visibility = patch.visibility() != null ? patch.visibility() : current.visibility();
        List<String> sports = patch.sportsSet() ? patch.sports() : current.sports();
        ExperienceLevel experience = patch.experienceSet() ? patch.experienceLevel() : current.experienceLevel();
        String city = patch.citySet() ? emptyToNull(patch.city()) : current.city();
        Double lat = patch.latSet() ? patch.lat() : current.lat();
        Double lng = patch.lngSet() ? patch.lng() : current.lng();
        List<PreferredWindow> windows = patch.windowsSet() ? patch.preferredWindows() : current.preferredWindows();
        UUID avatar = patch.avatarSet() ? patch.avatarMediaId() : current.avatarMediaId();
        return new Profile(userId, displayName, bio, visibility, sports, experience, city, lat, lng, windows, avatar);
    }

    private static String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
