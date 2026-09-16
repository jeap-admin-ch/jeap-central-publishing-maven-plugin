/*
 * Copyright (c) 2026-present Federal Office of Information Technology, Systems and Telecommunication FOITT.
 *
 * jEAP addition, not present in the upstream central-publishing-maven-plugin.
 */
package ch.admin.bit.jeap.central.publishing;

import java.io.PrintStream;

/**
 * Minimal logging for the HTTP client layer. The upstream client and endpoint classes are plain classes without a
 * Plexus logger, so messages are written to standard out using Maven's log prefixes, which makes them appear in the
 * build output like any other Maven message.
 */
public class PublishingLog
{
  private static final String MESSAGE_PREFIX = "jEAP central publishing: ";

  private static PrintStream out = System.out;

  private PublishingLog() {
  }

  public static void info(final String message) {
    out.println("[INFO] " + MESSAGE_PREFIX + message);
  }

  public static void warn(final String message) {
    out.println("[WARNING] " + MESSAGE_PREFIX + message);
  }

  /**
   * Redirects the output, for tests only.
   */
  static void setOut(final PrintStream printStream) {
    out = printStream != null ? printStream : System.out;
  }
}
