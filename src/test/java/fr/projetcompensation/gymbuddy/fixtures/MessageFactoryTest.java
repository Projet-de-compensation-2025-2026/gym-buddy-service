package fr.projetcompensation.gymbuddy.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageFactoryTest {

    @Test
    void conversationPairFollowsUnsignedPostgresOrder() {
        UUID highBit = UUID.fromString("c02690a4-cd9e-3d5f-aca7-a7543d75401d");
        UUID lowBit = UUID.fromString("1ef6bff6-a15a-37a3-848b-60bf408bd3aa");
        assertThat(highBit.compareTo(lowBit)).isNegative();
        assertThat(MessageFactory.lo(highBit, lowBit)).isEqualTo(lowBit);
        assertThat(MessageFactory.hi(highBit, lowBit)).isEqualTo(highBit);
    }
}
