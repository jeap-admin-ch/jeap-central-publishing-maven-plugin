/*
 * Copyright (c) 2022-present Sonatype, Inc. All rights reserved.
 * "Sonatype" is a trademark of Sonatype, Inc.
 */
package org.sonatype.central.publisher.client;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * Patched compared to upstream repo: makes the {@link PublisherClient} an ordinary JSR-330 binding.
 * <p>
 * Upstream registers the client with the Plexus container at runtime, from the {@code contextualize} callback of
 * {@code PlexusContextConfigImpl}. That only works for Plexus {@code @Requirement} injection, which resolves lazily;
 * with JSR-330 the client has to be bound before the components that inject it are created. Sisu binds a
 * {@code @Named} {@link Provider} to the type it provides, so this class replaces the upstream mechanism.
 */
@Named
@Singleton
public class PublisherClientProvider
    implements Provider<PublisherClient>
{
  @Override
  public PublisherClient get() {
    return PublisherClientFactory.createPublisherClient();
  }
}
