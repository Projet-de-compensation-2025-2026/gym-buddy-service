package fr.projetcompensation.gymbuddy.admin;

import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import java.util.List;

public interface AdminCatalog {

    List<ListedAdminUser> listUsers(String q, String role, String status, InstantIdCursor after, int limit);

    long countAdmins();

    List<ListedAdminMedia> listMedia(String q, InstantIdCursor after, int limit);

    List<ListedAdminContent> listContent(String type, String q, Boolean hidden, InstantIdCursor after, int limit);
}
