package fr.projetcompensation.gymbuddy.profiles.http;

import fr.projetcompensation.gymbuddy.openapi.model.PreferredWindow;
import fr.projetcompensation.gymbuddy.openapi.model.Profile;
import fr.projetcompensation.gymbuddy.profiles.ExperienceLevel;
import fr.projetcompensation.gymbuddy.profiles.VisibleProfile;

final class ProfileResponses {

    private ProfileResponses() {}

    static Profile toApi(VisibleProfile visible) {
        Profile body = new Profile(
                Profile.ViewEnum.fromValue(visible.view().name().toLowerCase()),
                visible.owner().handle(),
                Profile.VisibilityEnum.fromValue(visible.profile().visibility().wireValue()));
        if (visible.full()) {
            body.setDisplayName(visible.profile().displayName());
            body.setBio(visible.profile().bio());
            body.setSports(visible.profile().sports());
            body.setExperienceLevel(experience(visible.profile().experienceLevel()));
            body.setCity(visible.profile().city());
            body.setLat(visible.profile().lat());
            body.setLng(visible.profile().lng());
            body.setPreferredWindows(visible.profile().preferredWindows().stream()
                    .map(window -> new PreferredWindow(window.weekday(), window.start(), window.end()))
                    .toList());
            body.setAvatarMediaId(visible.profile().avatarMediaId());
            body.setFriendCount(visible.friendCount());
        } else {
            body.setAvatarMediaId(visible.profile().avatarMediaId());
        }
        return body;
    }

    private static Profile.ExperienceLevelEnum experience(ExperienceLevel level) {
        if (level == null) {
            return null;
        }
        return Profile.ExperienceLevelEnum.fromValue(level.wireValue());
    }
}
