package org.ayosynk.antiRedstoneLag.bootstrap;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Resolves sqlite-jdbc via Paper's library loader if not already provided by the server runtime.
 */
@SuppressWarnings("UnstableApiUsage")
public class PluginLibraryLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        try {
            Class.forName("org.sqlite.JDBC");
            return;
        } catch (ClassNotFoundException ignored) {
        }

        try {
            MavenLibraryResolver resolver = new MavenLibraryResolver();
            resolver.addRepository(new RemoteRepository.Builder(
                    "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
            resolver.addDependency(new Dependency(
                    new DefaultArtifact("org.xerial:sqlite-jdbc:3.49.1.0"), null));
            classpathBuilder.addLibrary(resolver);
        } catch (Throwable ignored) {
            // StorageService automatically falls back to atomic JSON persistence if SQLite cannot load.
        }
    }
}
