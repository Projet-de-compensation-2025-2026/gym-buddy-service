package fr.projetcompensation.gymbuddy.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StockImagesTest {

    @Test
    void stockJpegHasPositiveBytesAndTenKeys() {
        assertThat(StockImages.bytes()).isGreaterThan(0);
        assertThat(StockImages.jpeg()).hasSize(StockImages.bytes());
        assertThat(StockImages.keys()).hasSize(StockImages.COUNT).doesNotHaveDuplicates();
    }
}
