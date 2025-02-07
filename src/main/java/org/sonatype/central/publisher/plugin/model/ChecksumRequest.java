/*
 * Copyright (c) 2022-present Sonatype, Inc. All rights reserved.
 * "Sonatype" is a trademark of Sonatype, Inc.
 */
package org.sonatype.central.publisher.plugin.model;

import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Simple enum holding values on which Checksum are requested to be used.
 */
public enum ChecksumRequest
{
  ALL, // Will request MD5, SHA1, SHA256 and SHA512 to be generated
  REQUIRED, // Only MD5 and SHA1 will be requested to be generated
  NONE; // No Checksums will be requested to be generated.

  public static boolean isValidValue(final String value) {
    try {
      valueOf(value.toUpperCase());
      return true;
    }
    catch (IllegalArgumentException ignore) {
      return false;
    }
  }

  public static List<String> toNames() {
    return Arrays.stream(ChecksumRequest.values()).map(Enum::name).map(String::toLowerCase).collect(toList());
  }
}
