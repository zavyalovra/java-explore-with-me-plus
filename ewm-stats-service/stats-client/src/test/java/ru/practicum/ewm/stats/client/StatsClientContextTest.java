package ru.practicum.ewm.stats.client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class StatsClientContextTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            JacksonAutoConfiguration.class))
                    .withUserConfiguration(StatsClient.class)
                    .withPropertyValues(
                            "stats-server.url=http://localhost:9090");

    @Test
    void statsClientShouldBeSpringBean() {
        contextRunner.run(context ->
                assertThat(context).hasSingleBean(StatsClient.class)
        );
    }
}
