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

package com.demonwav.mcdev.platform.mcp.gradle.tooling;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class McpModelRFGBuilderImpl implements ModelBuilderService {

    @Override
    public boolean canBuild(String modelName) {
        return McpModelRFG.class.getName().equals(modelName);
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        Object extension = project.getExtensions().findByName("minecraft");
        if (extension == null) {
            return null;
        }

        // Only present on RetroFuturaGradle projects, so this cannot be confused with other ForgeGradle-likes
        Object mcpTasks = project.getExtensions().findByName("mcpTasks");
        if (mcpTasks == null) {
            return null;
        }

        Task genSrgMappings = project.getTasks().findByName("generateForgeSrgMappings");
        if (genSrgMappings == null) {
            return null;
        }

        try {
            String minecraftVersion = getProviderValue(ReflectUtil.getProperty(extension, "mcVersion"));
            String mappingChannel = getProviderValue(ReflectUtil.getProperty(extension, "mcpMappingChannel"));
            String mappingVersion = getProviderValue(ReflectUtil.getProperty(extension, "mcpMappingVersion"));
            if (minecraftVersion == null || mappingChannel == null || mappingVersion == null) {
                return null;
            }

            Set<String> mappingFiles = new HashSet<>();
            for (File file : genSrgMappings.getOutputs().getFiles().getFiles()) {
                mappingFiles.add(file.getAbsolutePath());
            }

            List<File> accessTransformers = new ArrayList<>();
            Object atObj = ReflectUtil.getProperty(mcpTasks, "deobfuscationATs");
            if (atObj instanceof FileCollection) {
                accessTransformers.addAll(((FileCollection) atObj).getFiles());
            }

            return new McpModelRFGImpl(
                    minecraftVersion,
                    mappingChannel + "-" + mappingVersion,
                    mappingFiles,
                    accessTransformers
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static String getProviderValue(Object obj) {
        if (obj instanceof Provider) {
            obj = ((Provider<?>) obj).getOrNull();
        }
        return obj != null ? obj.toString() : null;
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
                .withTitle("MinecraftDev: RetroFuturaGradle import error")
                .withText("Unable to build MinecraftDev RetroFuturaGradle model for project " + project.getDisplayName())
                .withException(exception)
                .reportMessage(project);
    }
}
