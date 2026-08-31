package fr.projetcompensation.gymbuddy.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationTest {

    /** Live v1.1.0 pair that 500'd: Java signed order is the opposite of PostgreSQL unsigned. */
    private static final UUID PG_LO = UUID.fromString("760a6a67-3de6-4b09-8437-9d292869512d");

    private static final UUID JAVA_LO = UUID.fromString("afcada6c-62a6-4059-853e-5f256b5d86f1");

    @Test
    void loHiFollowUnsignedPostgresOrderWhenJavaCompareToDisagrees() {
        assertThat(JAVA_LO.compareTo(PG_LO)).isNegative();
        assertThat(Conversation.lo(PG_LO, JAVA_LO)).isEqualTo(PG_LO);
        assertThat(Conversation.hi(PG_LO, JAVA_LO)).isEqualTo(JAVA_LO);
        assertThat(Conversation.lo(JAVA_LO, PG_LO)).isEqualTo(PG_LO);
        assertThat(Conversation.hi(JAVA_LO, PG_LO)).isEqualTo(JAVA_LO);
    }
}
