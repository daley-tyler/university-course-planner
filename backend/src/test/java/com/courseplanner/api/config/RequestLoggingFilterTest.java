package com.courseplanner.api.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {
    @Test
    void neverLogsAuthenticationQueryParameters() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        Level previous = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start(); logger.addAppender(appender); logger.setLevel(Level.DEBUG);
        try {
            var request = new MockHttpServletRequest("GET", "/login/cas");
            request.setQueryString("ticket=ST-regression-secret&token=private-value");
            new RequestLoggingFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
            var messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(messages).hasSize(2).allSatisfy(message -> {
                assertThat(message).contains("/login/cas");
                assertThat(message).doesNotContain("regression-secret", "private-value", "ticket=", "token=");
            });
        } finally {
            logger.detachAppender(appender); logger.setLevel(previous); appender.stop();
        }
    }
}
