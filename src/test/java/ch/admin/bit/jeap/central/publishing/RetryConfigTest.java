/*
 * Copyright (c) 2026-present Federal Office of Information Technology, Systems and Telecommunication FOITT.
 *
 * jEAP addition, not present in the upstream central-publishing-maven-plugin.
 */
package ch.admin.bit.jeap.central.publishing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static ch.admin.bit.jeap.central.publishing.RetryConfig.DEFAULT_MAX_RETRIES;
import static ch.admin.bit.jeap.central.publishing.RetryConfig.DEFAULT_SOCKET_TIMEOUT_SECONDS;
import static ch.admin.bit.jeap.central.publishing.RetryConfig.MAX_RETRIES;
import static ch.admin.bit.jeap.central.publishing.RetryConfig.RETRY_INITIAL_DELAY_SECONDS;
import static ch.admin.bit.jeap.central.publishing.RetryConfig.RETRY_MAX_DELAY_SECONDS;
import static ch.admin.bit.jeap.central.publishing.RetryConfig.RETRY_ON_AMBIGUOUS_FAILURE;
import static ch.admin.bit.jeap.central.publishing.RetryConfig.SOCKET_TIMEOUT_SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryConfigTest
{
  @AfterEach
  void clearProperties() {
    System.clearProperty(SOCKET_TIMEOUT_SECONDS);
    System.clearProperty(MAX_RETRIES);
    System.clearProperty(RETRY_INITIAL_DELAY_SECONDS);
    System.clearProperty(RETRY_MAX_DELAY_SECONDS);
    System.clearProperty(RETRY_ON_AMBIGUOUS_FAILURE);
  }

  @Test
  void defaultsAreUsedWhenNoPropertiesAreSet() {
    RetryConfig config = RetryConfig.fromSystemProperties();

    assertEquals(DEFAULT_SOCKET_TIMEOUT_SECONDS, config.getSocketTimeoutSeconds());
    assertEquals(DEFAULT_MAX_RETRIES, config.getMaxRetries());
    assertEquals(DEFAULT_MAX_RETRIES + 1, config.getMaxAttempts());
    assertTrue(config.isRetryOnAmbiguousFailure());
  }

  @Test
  void propertiesOverrideDefaults() {
    System.setProperty(SOCKET_TIMEOUT_SECONDS, "42");
    System.setProperty(MAX_RETRIES, "0");
    System.setProperty(RETRY_ON_AMBIGUOUS_FAILURE, "false");

    RetryConfig config = RetryConfig.fromSystemProperties();

    assertEquals(42, config.getSocketTimeoutSeconds());
    assertEquals(0, config.getMaxRetries());
    assertEquals(1, config.getMaxAttempts());
    assertFalse(config.isRetryOnAmbiguousFailure());
  }

  @Test
  void invalidValuesFallBackToTheDefaults() {
    System.setProperty(SOCKET_TIMEOUT_SECONDS, "not a number");
    System.setProperty(MAX_RETRIES, "-1");

    RetryConfig config = RetryConfig.fromSystemProperties();

    assertEquals(DEFAULT_SOCKET_TIMEOUT_SECONDS, config.getSocketTimeoutSeconds());
    assertEquals(DEFAULT_MAX_RETRIES, config.getMaxRetries());
  }

  @Test
  void emptyValuesFallBackToTheDefaults() {
    System.setProperty(SOCKET_TIMEOUT_SECONDS, "  ");

    assertEquals(DEFAULT_SOCKET_TIMEOUT_SECONDS, RetryConfig.fromSystemProperties().getSocketTimeoutSeconds());
  }

  @Test
  void retryDelayGrowsExponentiallyUpToTheConfiguredMaximum() {
    System.setProperty(RETRY_INITIAL_DELAY_SECONDS, "10");
    System.setProperty(RETRY_MAX_DELAY_SECONDS, "60");

    RetryConfig config = RetryConfig.fromSystemProperties();

    assertEquals(10_000L, config.getRetryDelayMillis(1));
    assertEquals(20_000L, config.getRetryDelayMillis(2));
    assertEquals(40_000L, config.getRetryDelayMillis(3));
    assertEquals(60_000L, config.getRetryDelayMillis(4));
    assertEquals(60_000L, config.getRetryDelayMillis(10));
  }

  @Test
  void retryDelayCanBeSwitchedOff() {
    System.setProperty(RETRY_INITIAL_DELAY_SECONDS, "0");
    System.setProperty(RETRY_MAX_DELAY_SECONDS, "0");

    assertEquals(0L, RetryConfig.fromSystemProperties().getRetryDelayMillis(3));
  }
}
