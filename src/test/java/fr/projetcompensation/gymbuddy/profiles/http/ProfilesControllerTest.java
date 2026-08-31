package fr.projetcompensation.gymbuddy.profiles.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.projetcompensation.gymbuddy.auth.AuthPrincipal;
import fr.projetcompensation.gymbuddy.auth.http.AccessTokenFilter;
import fr.projetcompensation.gymbuddy.http.ApiExceptionHandler;
import fr.projetcompensation.gymbuddy.profiles.ExperienceLevel;
import fr.projetcompensation.gymbuddy.profiles.PreferredWindow;
import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.profiles.ProfilePatch;
import fr.projetcompensation.gymbuddy.profiles.ProfileService;
import fr.projetcompensation.gymbuddy.profiles.ProfileVisibility;
import fr.projetcompensation.gymbuddy.profiles.VisibleProfile;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRole;
import fr.projetcompensation.gymbuddy.users.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = ProfilesController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AccessTokenFilter.class))
@Import({ApiExceptionHandler.class, PatchProfileBodyAdvice.class})
class ProfilesControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");
    private static final AuthPrincipal MEMBER = new AuthPrincipal(USER_ID, "blake", UserRole.MEMBER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;

    @Test
    void fsProf02_visibilityOnlyDoesNotMarkSportsOrWindowsSet() throws Exception {
        when(profileService.patchMe(eq(USER_ID), any())).thenReturn(fullProfile());

        mockMvc.perform(patch("/api/v1/profiles/me")
                        .requestAttr(AuthPrincipal.REQUEST_ATTRIBUTE, MEMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"private\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ProfilePatch> captor = ArgumentCaptor.forClass(ProfilePatch.class);
        verify(profileService).patchMe(eq(USER_ID), captor.capture());
        ProfilePatch patch = captor.getValue();
        assertThat(patch.sportsSet()).isFalse();
        assertThat(patch.windowsSet()).isFalse();
        assertThat(patch.bioSet()).isFalse();
        assertThat(patch.visibility()).isEqualTo(ProfileVisibility.PRIVATE);
    }

    @Test
    void fsProf06_unknownExperienceLevelIsValidation() throws Exception {
        mockMvc.perform(patch("/api/v1/profiles/me")
                        .requestAttr(AuthPrincipal.REQUEST_ATTRIBUTE, MEMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experienceLevel\":\"elite\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"));
    }

    private VisibleProfile fullProfile() {
        User owner = new User(USER_ID, "blake@example.com", "blake", "hash", UserRole.MEMBER, UserStatus.ACTIVE, NOW);
        Profile profile = new Profile(
                USER_ID,
                "Blake",
                "bio",
                ProfileVisibility.PRIVATE,
                List.of("running"),
                ExperienceLevel.ADVANCED,
                "Austin, TX",
                30.0,
                -97.0,
                List.of(new PreferredWindow(1, "06:00", "08:00")),
                null);
        return VisibleProfile.full(owner, profile, 0);
    }
}
