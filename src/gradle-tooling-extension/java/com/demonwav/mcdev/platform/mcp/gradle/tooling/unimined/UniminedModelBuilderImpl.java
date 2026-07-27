/*
 * Minecraft Development for IntelliJ
 *
 * https://mcdev.io/
 *
 * Copyright (C) 2026 minecraft-dev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, version 3.0 only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.demonwav.mcdev.platform.mcp.gradle.tooling.unimined;

import com.demonwav.mcdev.platform.mcp.gradle.tooling.ReflectUtil;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class UniminedModelBuilderImpl implements ModelBuilderService {

    @Override
    public boolean canBuild(String modelName) {
        return UniminedModel.class.getName().equals(modelName);
    }

    @Override
    public @Nullable Object buildAll(String modelName, Project project) {
        if (project.getPlugins().findPlugin("xyz.wagyourtail.unimined") == null) {
            return null;
        }

        Object unimined = project.getExtensions().findByName("unimined");
        if (unimined == null) {
            return null;
        }

        Map<?, ?> minecrafts;
        try {
            minecrafts = (Map<?, ?>) ReflectUtil.getProperty(unimined, "minecrafts");
        } catch (Exception e) {
            return null;
        }
        if (minecrafts == null || minecrafts.isEmpty()) {
            return null;
        }

        List<UniminedModel.SourceSetEntry> entries = new ArrayList<>();
        for (Map.Entry<?, ?> entry : minecrafts.entrySet()) {
            UniminedModel.SourceSetEntry built = buildSourceSetEntry(project, entry.getKey(), entry.getValue());
            if (built != null) {
                entries.add(built);
            }
        }
        if (entries.isEmpty()) {
            return null;
        }

        return new UniminedModelImpl(entries);
    }

    private @Nullable UniminedModel.SourceSetEntry buildSourceSetEntry(
            Project project,
            Object sourceSet,
            Object minecraftConfig
    ) {
        try {
            String sourceSetName = (String) ReflectUtil.getProperty(sourceSet, "name");
            if (sourceSetName == null) {
                return null;
            }

            // Both throw when unset, skipping this source set
            String version = (String) ReflectUtil.getProperty(minecraftConfig, "version");
            Object patcher = ReflectUtil.getProperty(minecraftConfig, "mcPatcher");
            if (version == null || patcher == null) {
                return null;
            }

            // UniminedExtension.getLocalCache(sourceSet): <projectDir>/.gradle/unimined/local[/<sourceSet>]
            Path localCache = project.getProjectDir().toPath()
                    .resolve(".gradle").resolve("unimined").resolve("local");
            if (!"main".equals(sourceSetName)) {
                localCache = localCache.resolve(sourceSetName);
            }

            boolean fabricLike = isInstanceOf(patcher, "FabricLikeMinecraftTransformer");
            File tinyFile = null;
            File srgFile = null;
            List<File> accessTransformers = Collections.emptyList();

            if (fabricLike) {
                tinyFile = resolveTinyMappings(patcher, localCache);
            } else if (isInstanceOf(patcher, "ForgeLikeMinecraftTransformer")) {
                srgFile = resolveSrgMappings(patcher, localCache);
                accessTransformers = resolveAccessTransformers(patcher);
            }

            return new UniminedModelImpl.SourceSetEntryImpl(
                    sourceSetName, version, fabricLike, tinyFile, srgFile, accessTransformers);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Locates the Tiny v2 mappings file for Fabric-like patchers. Unimined exports it next to the
     * dev-mappings jar as {@code <localCache>/mappings/mappings.tiny}, with the intermediary and
     * named namespace labels MinecraftDev's tiny consumers look for.
     */
    private @Nullable File resolveTinyMappings(Object fabricPatcher, Path localCache) {
        Path tinyPath = localCache.resolve("mappings").resolve("mappings.tiny");
        if (Files.exists(tinyPath)) {
            return tinyPath.toFile();
        }

        // Trigger unimined's lazy export; the property holds the wrapping jar, the tiny file
        // is written as its sibling. Null when the minecraft jar is not obfuscated.
        try {
            Object devMappings = ReflectUtil.getProperty(fabricPatcher, "devMappings");
            if (devMappings instanceof Path) {
                Path sibling = ((Path) devMappings).resolveSibling("mappings.tiny");
                if (Files.exists(sibling)) {
                    return sibling.toFile();
                }
            }
        } catch (Exception ignored) {
        }
        if (Files.exists(tinyPath)) {
            return tinyPath.toFile();
        }
        return null;
    }

    /**
     * Locates the SRG mappings for Forge-like patchers. Unimined's {@code srgToMCPAsSRG} export is
     * laid out searge -> named, while StandardSrgParser expects the {@code mcp-srg.srg} layout
     * (named -> searge), so a column-swapped copy is written next to it and returned instead.
     */
    private @Nullable File resolveSrgMappings(Object forgePatcher, Path localCache) {
        Path source = localCache.resolve("mappings").resolve("srg2mcp.srg");
        if (!Files.exists(source)) {
            // Trigger unimined's lazy export. Throws when the mappings have no searge namespace
            // (e.g. modern NeoForge), in which case there is nothing to provide.
            try {
                Object exported = ReflectUtil.getProperty(forgePatcher, "srgToMCPAsSRG");
                if (exported instanceof Path) {
                    source = (Path) exported;
                }
            } catch (Exception ignored) {
            }
        }
        if (!Files.exists(source)) {
            return null;
        }

        try {
            Path swapped = source.resolveSibling("mcdev-named2srg.srg");
            if (!Files.exists(swapped)
                    || Files.getLastModifiedTime(swapped).compareTo(Files.getLastModifiedTime(source)) < 0) {
                writeSwappedSrg(source, swapped);
            }
            return swapped.toFile();
        } catch (IOException e) {
            return null;
        }
    }

    private void writeSwappedSrg(Path source, Path target) throws IOException {
        Set<String> swapped = new LinkedHashSet<>();
        for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
            String[] parts = line.split(" ");
            if (parts.length == 3 && ("CL:".equals(parts[0]) || "FD:".equals(parts[0]) || "PK:".equals(parts[0]))) {
                swapped.add(parts[0] + ' ' + parts[2] + ' ' + parts[1]);
            } else if (parts.length == 5 && "MD:".equals(parts[0])) {
                swapped.add(parts[0] + ' ' + parts[3] + ' ' + parts[4] + ' ' + parts[1] + ' ' + parts[2]);
            } else {
                swapped.add(line);
            }
        }
        Files.write(target, swapped, StandardCharsets.UTF_8);
    }

    private List<File> resolveAccessTransformers(Object forgePatcher) {
        try {
            if (ReflectUtil.hasProperty(forgePatcher, "accessTransformer")) {
                Object at = ReflectUtil.getProperty(forgePatcher, "accessTransformer");
                if (at instanceof File) {
                    return Collections.singletonList((File) at);
                }
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyList();
    }

    private static boolean isInstanceOf(Object obj, String simpleName) {
        for (Class<?> clazz = obj.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            if (simpleName.equals(clazz.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void reportErrorMessage(
            final @NotNull String modelName,
            final @NotNull Project project,
            final @NotNull ModelBuilderContext context,
            final @NotNull Exception exception
    ) {
        //noinspection UnstableApiUsage
        context.getMessageReporter().createMessage()
                .withGroup(this)
                .withKind(Message.Kind.ERROR)
                .withGroup("com.demonwav.mcdev")
                .withTitle("MinecraftDev: Unimined import error")
                .withText("Unable to build MinecraftDev Unimined model for project " + project.getDisplayName())
                .withException(exception)
                .reportMessage(project);
    }
}
