package fr.projetcompensation.gymbuddy.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OpenApiPackagePinTest {

    @Test
    void generateSourcesPinsVersionedPackageRefTree() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        assertThat(pom).contains("<openapi.package.tag>740b05348288b0057af5e807b31768e7afa75555</openapi.package.tag>");
        assertThat(pom).contains("<openapi.spec.file>${openapi.package.dir}/openapi/openapi.yaml</openapi.spec.file>");
        assertThat(pom).contains("<inputSpec>${openapi.spec.file}</inputSpec>");
        assertThat(pom).contains("getHealthz_200_response=HealthStatus");
        assertThat(pom).contains("postAuthRegister_request=RegisterRequest");
        assertThat(pom).contains("getProfilesMe_200_response=Profile");
        assertThat(pom).contains("postAuthPassword_request=ChangePasswordRequest");
        assertThat(pom).contains("postFriendships_request=CreateFriendshipRequest");
        assertThat(pom).contains("postMedia_request=CreateMediaRequest");
        assertThat(pom).contains("getMediaIdUrl_200_response=MediaUrlResponse");
        assertThat(pom).contains("postPosts_request=CreatePostRequest");
        assertThat(pom).contains("getPostsIdLikes_200_response=PostLikerPage");
        assertThat(pom).contains("postPostsIdComments_request=CreateCommentRequest");
        assertThat(pom).contains("getPostsIdComments_200_response=CommentPage");
        assertThat(pom).contains("getCommentsIdReplies_200_response=CommentPage");
        assertThat(pom).contains("getFeed_200_response=FeedPage");
        assertThat(pom).contains("getFeed_200_response_data_inner=FeedItem");
        assertThat(pom).contains("postEvents_request=CreateEventRequest");
        assertThat(pom).contains("getEvents_200_response=EventPage");
        assertThat(pom).contains("postApplicationsIdAccept_200_response=EventApplication");
        assertThat(pom).contains("getSearchPeople_200_response=PeopleSearchPage");
        assertThat(pom).contains("getSearchEvents_200_response=EventSearchPage");
        assertThat(pom).contains("getSearchEvents_200_response_data_inner_organizer=PostAuthor");
        assertThat(pom).contains("getAdminUsers_200_response=AdminUserPage");
        assertThat(pom).contains("patchAdminUsersIdRole_request=PatchUserRoleRequest");
        assertThat(pom).contains("postAdminContentTypeIdHide_request=HideContentRequest");
        assertThat(pom).contains("postReports_request=CreateReportRequest");
        assertThat(pom).contains("postAdminFixtures_request=GenerateFixturesRequest");
        assertThat(pom).contains("getAdminAudit_200_response=AuditEventPage");
        assertThat(pom).contains("getSuggestions_200_response=SuggestionPage");
        assertThat(pom).contains("getMatchingMe_200_response=MatchingMe");
        assertThat(pom).contains("postConversations_request=CreateConversationRequest");
        assertThat(pom).contains("getConversations_200_response=ConversationPage");
        assertThat(pom).contains("postConversationsIdMessages_request=CreateMessageRequest");
        assertThat(pom).contains("getConversationsIdMessages_200_response=MessagePage");
        assertThat(pom).doesNotContain("bundled.yaml");
        assertThat(pom)
                .doesNotContain("raw.githubusercontent.com/Projet-de-compensation-2025-2026/gym-buddy-openapi/develop");
    }
}
