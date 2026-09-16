/*
 * Copyright (c) 2026-present Federal Office of Information Technology, Systems and Telecommunication FOITT.
 *
 * jEAP addition, not present in the upstream central-publishing-maven-plugin.
 */
package ch.admin.bit.jeap.central.publishing;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Records whether a bundle upload has been re-sent after a failure that left it unclear whether the Central Publisher
 * Portal had already received the bundle (typically a socket read timeout while waiting for the response).
 * <p>
 * In that case there may be two deployments for the same components, of which one will fail. The publish mojo reads
 * this flag to decide whether such a failure may be reconciled against the components actually published on Central
 * instead of failing the build.
 * <p>
 * The state is static because it is written in the HTTP client layer and read in the mojo, which have no common
 * object graph. A Maven build publishes at most one bundle per reactor, in the last project of the reactor.
 */
public class UploadRetryState
{
  private static final AtomicBoolean ambiguousUploadRetry = new AtomicBoolean(false);

  private UploadRetryState() {
  }

  public static void markAmbiguousUploadRetry() {
    ambiguousUploadRetry.set(true);
  }

  public static boolean wasAmbiguouslyRetried() {
    return ambiguousUploadRetry.get();
  }

  public static void reset() {
    ambiguousUploadRetry.set(false);
  }
}
