package fr.projetcompensation.gymbuddy.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OpenApiPackagePinTest {

    @Test
    void generateSourcesPinsVersionedPackageRefTree() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        assertThat(pom).contains("<openapi.package.tag>01ab3d50195833296b10e8ca44aa89d1e046683a</openapi.package.tag>");
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
        assertThat(pom).doesNotContain("bundled.yaml");
        assertThat(pom)
                .doesNotContain("raw.githubusercontent.com/Projet-de-compensation-2025-2026/gym-buddy-openapi/develop");
    }
}
