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

package com.demonwav.mcdev.platform.mcp.gradle.tooling.mcpforge;

import com.demonwav.mcdev.platform.mcp.gradle.tooling.McpModelMDG;
import com.demonwav.mcdev.platform.mcp.gradle.tooling.ReflectUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;

public final class McpModDevForgeModelBuilderImpl implements ModelBuilderService {

    @Override
    public boolean canBuild(String modelName) {
        return McpModelMDG.class.getName().equals(modelName);
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        if (project.getPlugins().findPlugin("net.neoforged.moddev.mcpforge") == null) {
            return null;
        }

        Object extension = project.getExtensions().findByName("mcpForge");
        if (extension == null) {
            return null;
        }

        // createMinecraftArtifacts is only registered once mcpForge.enable {} has run
        if (project.getTasks().findByName("createMinecraftArtifacts") == null) {
            return null;
        }

        // Provided by ModDevExtension after enable(); throws before that, hence the guarded reflection
        String minecraftVersion = getStringProperty(extension, "minecraftVersion");
        // Forge/Cleanroom platform version; throws in MCP-only (vanilla) mode
        String platformVersion = getStringProperty(extension, "version");
        // MCP mapping version, resolved from the NeoForm dependency
        String mcpVersion = getStringProperty(extension, "mcpVersion");

        List<File> accessTransformers = new ArrayList<>();
        try {
            Object atCollection = ReflectUtil.getProperty(extension, "accessTransformers");
            if (atCollection != null) {
                Object filesObj = ReflectUtil.getProperty(atCollection, "files");
                if (filesObj instanceof FileCollection) {
                    accessTransformers.addAll(((FileCollection) filesObj).getFiles());
                } else if (filesObj instanceof Iterable) {
                    for (Object file : (Iterable<?>) filesObj) {
                        if (file instanceof File) {
                            accessTransformers.add((File) file);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // MDG writes all mapping artifacts to a fixed location below the build directory. The file only
        // exists after createMinecraftArtifacts has run (MDG wires it as an IDE sync task itself).
        File mappingsFile = null;
        try {
            Directory artifactsDir = project.getLayout().getBuildDirectory().dir("moddev/artifacts").getOrNull();
            if (artifactsDir != null) {
                File candidate = new File(artifactsDir.getAsFile(), "namedToIntermediate.tsrg");
                if (candidate.exists()) {
                    mappingsFile = candidate;
                }
            }
        } catch (Exception ignored) {
        }

        return new McpModDevForgeModelImpl(
                minecraftVersion, platformVersion, mcpVersion, mappingsFile, accessTransformers);
    }

    private static String getStringProperty(Object obj, String name) {
        try {
            Object value = ReflectUtil.getProperty(obj, name);
            if (value instanceof Provider) {
                value = ((Provider<?>) value).getOrNull();
            }
            if (value instanceof String) {
                return (String) value;
            }
        } catch (Exception ignored) {
        }
        return null;
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
                .withTitle("MinecraftDev: MDG MCPForge import error")
                .withText("Unable to build MinecraftDev MDG MCPForge model for project " + project.getDisplayName())
                .withException(exception)
                .reportMessage(project);
    }
}
