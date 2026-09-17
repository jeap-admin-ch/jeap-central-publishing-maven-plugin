/*
 * Copyright (c) 2026-present Federal Office of Information Technology, Systems and Telecommunication FOITT.
 *
 * jEAP addition, not present in the upstream central-publishing-maven-plugin.
 */
package ch.admin.bit.jeap.central.publishing;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import javax.inject.Named;
import javax.inject.Provider;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.sonatype.central.publisher.plugin.DeployLifecycleParticipant;
import org.sonatype.central.publisher.plugin.PublishMojo;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards how the plugin's components are wired at runtime.
 * <p>
 * The components are JSR-330 beans, discovered through the index that {@code sisu-maven-plugin} writes to
 * {@code META-INF/sisu/javax.inject.Named}; the mojo's dependencies on them are declared in the plugin descriptor
 * that {@code maven-plugin-plugin} writes to {@code META-INF/maven/plugin.xml}. Maven resolves the two against each
 * other at build time, in a container this test suite never sees: the maven-plugin-testing-harness brings its own.
 * A component that loses its annotation, or an upgrade that stops generating the index, would therefore go unnoticed
 * until a release fails with a lookup error - which is what this test is here to prevent.
 */
class ComponentWiringTest
{
  /**
   * Roles the Maven runtime itself provides to a plugin; they are never in this plugin's own index.
   */
  private static final String MAVEN_PROVIDED_ROLE_PREFIX = "org.apache.maven.";

  private static final Path CLASSES = codeSourceOf(PublishMojo.class);

  private static final Path SISU_INDEX = CLASSES.resolve("META-INF/sisu/javax.inject.Named");

  private static final Path PLUGIN_DESCRIPTOR = CLASSES.resolve("META-INF/maven/plugin.xml");

  /**
   * Java 11, the level {@code maven.compiler.release} is set to. Java 8 would be 52, Java 21 is 65.
   */
  private static final int MAX_CLASS_FILE_MAJOR_VERSION = 55;

  @Test
  void theComponentIndexIsGenerated() throws IOException {
    assertTrue(Files.exists(SISU_INDEX),
        "sisu-maven-plugin should have written " + SISU_INDEX + "; without it Maven finds no component at all");
    assertFalse(indexedComponents().isEmpty(), "the component index should not be empty");
  }

  /**
   * The upstream plugin used to register its {@code PublisherClient} with the Plexus container at runtime, which
   * JSR-330 injection cannot do. This is the test that catches such a gap.
   */
  @Test
  void everyComponentTheMojoRequiresIsProvidedByAnIndexedComponentOrByMaven() throws Exception {
    Set<String> provided = new HashSet<>();
    for (Class<?> component : indexedComponents()) {
      provided.addAll(typesProvidedBy(component));
    }

    List<String> unsatisfied = new ArrayList<>();
    for (String role : mojoRequirements()) {
      if (!role.startsWith(MAVEN_PROVIDED_ROLE_PREFIX) && !provided.contains(role)) {
        unsatisfied.add(role);
      }
    }

    assertTrue(unsatisfied.isEmpty(),
        "no component in " + SISU_INDEX + " provides " + unsatisfied
            + ", so Maven cannot inject it into the mojo. Indexed components provide: " + provided);
  }

  @Test
  void everyIndexedComponentIsAnnotatedAndInstantiable() throws Exception {
    for (Class<?> component : indexedComponents()) {
      assertTrue(component.isAnnotationPresent(Named.class),
          component.getName() + " is indexed but not annotated with @Named");
      assertFalse(component.isInterface(), component.getName() + " is indexed but is an interface");
    }
  }

  /**
   * Maven looks the participant up by the hint it is registered under; renaming it silently unbinds the goal from
   * the deploy phase, and releases would stop publishing.
   */
  @Test
  void theLifecycleParticipantKeepsItsHint() throws Exception {
    assertTrue(indexedComponents().contains(DeployLifecycleParticipant.class),
        "the lifecycle participant should be indexed, otherwise Maven never calls it");
    assertEquals("org.sonatype.central.publisher.plugin.DeployLifecycleParticipant",
        DeployLifecycleParticipant.class.getAnnotation(Named.class).value());
  }

  /**
   * The class files of the plugin are read by the sisu class scanner of the Maven that runs the plugin, and that
   * scanner bundles an ASM as old as that Maven: Maven 3.9.9 cannot read class files newer than Java 21, older
   * Maven 3.9.x not even those. A class file the scanner cannot parse is skipped silently, which leaves every
   * component of the plugin unbound and fails the build of whoever publishes with it - not the build of this
   * plugin, which is why nothing else here would notice.
   */
  @Test
  void everyClassIsReadableByTheClassScannerOfAnOlderMaven() throws IOException {
    List<String> tooNew = new ArrayList<>();
    try (Stream<Path> classFiles = Files.walk(CLASSES)) {
      for (Path classFile : (Iterable<Path>) classFiles.filter(ComponentWiringTest::isClassFile)::iterator) {
        int majorVersion = majorVersionOf(classFile);
        if (majorVersion > MAX_CLASS_FILE_MAJOR_VERSION) {
          tooNew.add(CLASSES.relativize(classFile) + " (major version " + majorVersion + ")");
        }
      }
    }

    assertTrue(tooNew.isEmpty(),
        tooNew.size() + " class files are newer than Java " + (MAX_CLASS_FILE_MAJOR_VERSION - 44)
            + ", so the sisu scanner of an older Maven skips them and finds no component at all, for example "
            + tooNew.subList(0, Math.min(3, tooNew.size()))
            + ". Keep maven.compiler.release at " + (MAX_CLASS_FILE_MAJOR_VERSION - 44)
            + "; maven.compiler.testRelease is what raises the level for the tests.");
  }

  /**
   * The types a component can be injected as: itself, everything it extends or implements, and - for a
   * {@link Provider} - the type it provides.
   */
  private static Set<String> typesProvidedBy(final Class<?> component) {
    Set<String> types = new HashSet<>();
    for (Class<?> type = component; type != null && type != Object.class; type = type.getSuperclass()) {
      types.add(type.getName());
      for (Class<?> iface : type.getInterfaces()) {
        types.add(iface.getName());
      }
      for (Type generic : type.getGenericInterfaces()) {
        if (generic instanceof ParameterizedType parameterized
            && parameterized.getRawType() == Provider.class
            && parameterized.getActualTypeArguments()[0] instanceof Class<?> provided) {
          types.add(provided.getName());
        }
      }
    }
    return types;
  }

  private static Set<Class<?>> indexedComponents() throws IOException {
    Set<Class<?>> components = new HashSet<>();
    for (String line : Files.readAllLines(SISU_INDEX, StandardCharsets.UTF_8)) {
      String className = line.trim();
      if (className.isEmpty() || className.startsWith("#")) {
        continue;
      }
      try {
        components.add(Class.forName(className));
      }
      catch (ClassNotFoundException e) {
        fail("the component index lists " + className + ", which is not on the classpath");
      }
    }
    return components;
  }

  private static List<String> mojoRequirements()
      throws IOException, ParserConfigurationException, SAXException
  {
    assertTrue(Files.exists(PLUGIN_DESCRIPTOR), "maven-plugin-plugin should have written " + PLUGIN_DESCRIPTOR);
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(false);
    Document descriptor;
    try (var in = Files.newInputStream(PLUGIN_DESCRIPTOR)) {
      descriptor = factory.newDocumentBuilder().parse(in);
    }
    List<String> roles = new ArrayList<>();
    NodeList requirements = descriptor.getElementsByTagName("requirement");
    for (int i = 0; i < requirements.getLength(); i++) {
      NodeList role = ((Element) requirements.item(i)).getElementsByTagName("role");
      if (role.getLength() > 0) {
        roles.add(role.item(0).getTextContent().trim());
      }
    }
    assertFalse(roles.isEmpty(), "the publish mojo should declare the components it needs");
    return roles;
  }

  private static boolean isClassFile(final Path path) {
    return path.getFileName().toString().endsWith(".class");
  }

  /**
   * The major version a class file carries in the two bytes that follow its magic number and minor version.
   */
  private static int majorVersionOf(final Path classFile) throws IOException {
    try (DataInputStream in = new DataInputStream(Files.newInputStream(classFile))) {
      in.readInt(); // 0xCAFEBABE
      in.readUnsignedShort(); // minor version
      return in.readUnsignedShort();
    }
  }

  private static Path codeSourceOf(final Class<?> type) {
    try {
      return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
    catch (URISyntaxException e) {
      throw new IllegalStateException("cannot locate the build output of " + type, e);
    }
  }
}
