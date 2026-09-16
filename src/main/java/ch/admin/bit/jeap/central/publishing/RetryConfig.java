/*
 * Copyright (c) 2026-present Federal Office of Information Technology, Systems and Telecommunication FOITT.
 *
 * jEAP addition, not present in the upstream central-publishing-maven-plugin.
 */
package ch.admin.bit.jeap.central.publishing;

/**
 * Configuration of the retry behaviour of this fork, read from JVM system properties.
 * <p>
 * System properties are used instead of Maven plugin parameters on purpose: they keep the diff against the upstream
 * plugin minimal and can be set centrally via {@code MAVEN_OPTS} in the build infrastructure, without changing the
 * {@code pom.xml} of every project that publishes to Maven Central.
 */
public class RetryConfig
{
  public static final String PROPERTY_PREFIX = "jeap.centralPublishing.";

  public static final String CONNECT_TIMEOUT_SECONDS = PROPERTY_PREFIX + "connectTimeoutSeconds";

  public static final String SOCKET_TIMEOUT_SECONDS = PROPERTY_PREFIX + "socketTimeoutSeconds";

  public static final String MAX_RETRIES = PROPERTY_PREFIX + "maxRetries";

  public static final String RETRY_INITIAL_DELAY_SECONDS = PROPERTY_PREFIX + "retryInitialDelaySeconds";

  public static final String RETRY_MAX_DELAY_SECONDS = PROPERTY_PREFIX + "retryMaxDelaySeconds";

  public static final String RETRY_ON_AMBIGUOUS_FAILURE = PROPERTY_PREFIX + "retryOnAmbiguousFailure";

  public static final String PUBLISHED_GRACE_SECONDS = PROPERTY_PREFIX + "publishedGraceSeconds";

  public static final String PUBLISHED_POLL_INTERVAL_SECONDS = PROPERTY_PREFIX + "publishedPollIntervalSeconds";

  static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 60;

  static final int DEFAULT_SOCKET_TIMEOUT_SECONDS = 900;

  static final int DEFAULT_MAX_RETRIES = 4;

  static final int DEFAULT_RETRY_INITIAL_DELAY_SECONDS = 10;

  static final int DEFAULT_RETRY_MAX_DELAY_SECONDS = 120;

  static final boolean DEFAULT_RETRY_ON_AMBIGUOUS_FAILURE = true;

  static final int DEFAULT_PUBLISHED_GRACE_SECONDS = 600;

  static final int DEFAULT_PUBLISHED_POLL_INTERVAL_SECONDS = 15;

  private final int connectTimeoutSeconds;

  private final int socketTimeoutSeconds;

  private final int maxRetries;

  private final int retryInitialDelaySeconds;

  private final int retryMaxDelaySeconds;

  private final boolean retryOnAmbiguousFailure;

  private final int publishedGraceSeconds;

  private final int publishedPollIntervalSeconds;

  RetryConfig(
      final int connectTimeoutSeconds,
      final int socketTimeoutSeconds,
      final int maxRetries,
      final int retryInitialDelaySeconds,
      final int retryMaxDelaySeconds,
      final boolean retryOnAmbiguousFailure,
      final int publishedGraceSeconds,
      final int publishedPollIntervalSeconds)
  {
    this.connectTimeoutSeconds = connectTimeoutSeconds;
    this.socketTimeoutSeconds = socketTimeoutSeconds;
    this.maxRetries = maxRetries;
    this.retryInitialDelaySeconds = retryInitialDelaySeconds;
    this.retryMaxDelaySeconds = retryMaxDelaySeconds;
    this.retryOnAmbiguousFailure = retryOnAmbiguousFailure;
    this.publishedGraceSeconds = publishedGraceSeconds;
    this.publishedPollIntervalSeconds = publishedPollIntervalSeconds;
  }

  public static RetryConfig fromSystemProperties() {
    return new RetryConfig(
        intProperty(CONNECT_TIMEOUT_SECONDS, DEFAULT_CONNECT_TIMEOUT_SECONDS, 1),
        intProperty(SOCKET_TIMEOUT_SECONDS, DEFAULT_SOCKET_TIMEOUT_SECONDS, 1),
        intProperty(MAX_RETRIES, DEFAULT_MAX_RETRIES, 0),
        intProperty(RETRY_INITIAL_DELAY_SECONDS, DEFAULT_RETRY_INITIAL_DELAY_SECONDS, 0),
        intProperty(RETRY_MAX_DELAY_SECONDS, DEFAULT_RETRY_MAX_DELAY_SECONDS, 0),
        booleanProperty(RETRY_ON_AMBIGUOUS_FAILURE, DEFAULT_RETRY_ON_AMBIGUOUS_FAILURE),
        intProperty(PUBLISHED_GRACE_SECONDS, DEFAULT_PUBLISHED_GRACE_SECONDS, 0),
        intProperty(PUBLISHED_POLL_INTERVAL_SECONDS, DEFAULT_PUBLISHED_POLL_INTERVAL_SECONDS, 1));
  }

  /**
   * Delay to wait before the attempt following the given (1-based) failed attempt. Grows exponentially, capped at
   * {@link #getRetryMaxDelaySeconds()}.
   */
  public long getRetryDelayMillis(final int failedAttempt) {
    long delaySeconds = retryInitialDelaySeconds;
    for (int i = 1; i < failedAttempt && delaySeconds < retryMaxDelaySeconds; i++) {
      delaySeconds = delaySeconds * 2;
    }
    return Math.min(delaySeconds, retryMaxDelaySeconds) * 1000L;
  }

  public int getMaxAttempts() {
    return maxRetries + 1;
  }

  public int getConnectTimeoutSeconds() {
    return connectTimeoutSeconds;
  }

  public int getSocketTimeoutSeconds() {
    return socketTimeoutSeconds;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public int getRetryInitialDelaySeconds() {
    return retryInitialDelaySeconds;
  }

  public int getRetryMaxDelaySeconds() {
    return retryMaxDelaySeconds;
  }

  public boolean isRetryOnAmbiguousFailure() {
    return retryOnAmbiguousFailure;
  }

  public int getPublishedGraceSeconds() {
    return publishedGraceSeconds;
  }

  public int getPublishedPollIntervalSeconds() {
    return publishedPollIntervalSeconds;
  }

  private static int intProperty(final String name, final int defaultValue, final int minValue) {
    String value = System.getProperty(name);
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }

    try {
      int parsed = Integer.parseInt(value.trim());
      if (parsed < minValue) {
        PublishingLog.warn(String.format("Ignoring %s=%s, must be at least %d. Using %d.",
            name, value, minValue, defaultValue));
        return defaultValue;
      }
      return parsed;
    }
    catch (NumberFormatException e) {
      PublishingLog.warn(String.format("Ignoring %s=%s, not a number. Using %d.", name, value, defaultValue));
      return defaultValue;
    }
  }

  private static boolean booleanProperty(final String name, final boolean defaultValue) {
    String value = System.getProperty(name);
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }
    return Boolean.parseBoolean(value.trim());
  }
}
