package fr.projetcompensation.gymbuddy.profiles;

import fr.projetcompensation.gymbuddy.users.User;

public record VisibleProfile(View view, User owner, Profile profile, int friendCount) {

    public enum View {
        FULL,
        STUB
    }

    public static VisibleProfile full(User owner, Profile profile, int friendCount) {
        return new VisibleProfile(View.FULL, owner, profile, friendCount);
    }

    public static VisibleProfile stub(User owner, Profile profile) {
        return new VisibleProfile(View.STUB, owner, profile, 0);
    }

    public boolean full() {
        return view == View.FULL;
    }
}
