package dev.voidscape.journeys.core;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Downloads the database libraries (HikariCP, SQLite and MariaDB drivers) through Paper's library loader instead of
 * shading them, which keeps the plugin jar small and avoids clashing with other plugins' copies.
 *
 * <p>The coordinates come from {@code journeys-libraries.txt}, generated at build time from the version catalog.</p>
 */
public final class JourneysLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder builder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder("central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
        for (String coordinates : libraries()) {
            resolver.addDependency(new Dependency(new DefaultArtifact(coordinates), null));
        }
        builder.addLibrary(resolver);
    }

    private static String[] libraries() {
        try (InputStream in = JourneysLoader.class.getResourceAsStream("/journeys-libraries.txt")) {
            if (in == null) {
                throw new IllegalStateException("journeys-libraries.txt is missing from the plugin jar");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim().split("\\s*,\\s*");
        } catch (IOException e) {
            throw new IllegalStateException("Could not read journeys-libraries.txt", e);
        }
    }
}
