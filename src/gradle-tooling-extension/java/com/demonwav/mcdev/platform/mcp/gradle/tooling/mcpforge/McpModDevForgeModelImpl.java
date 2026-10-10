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

import java.io.File;
import java.io.Serializable;
import java.util.List;

import org.jetbrains.annotations.Nullable;

final class McpModDevForgeModelImpl implements McpModelMDG, Serializable {

    private final String minecraftVersion;
    private final String platformVersion;
    private final String mcpVersion;
    private final File mappingsFile;
    private final List<File> accessTransformers;

    McpModDevForgeModelImpl(
            String minecraftVersion,
            String platformVersion,
            String mcpVersion,
            File mappingsFile,
            List<File> accessTransformers
    ) {
        this.minecraftVersion = minecraftVersion;
        this.platformVersion = platformVersion;
        this.mcpVersion = mcpVersion;
        this.mappingsFile = mappingsFile;
        this.accessTransformers = accessTransformers;
    }

    @Override
    @Nullable
    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    @Override
    @Nullable
    public String getPlatformVersion() {
        return platformVersion;
    }

    @Override
    @Nullable
    public String getMcpVersion() {
        return mcpVersion;
    }

    @Override
    @Nullable
    public File getMappingsFile() {
        return mappingsFile;
    }

    @Override
    public List<File> getAccessTransformers() {
        return accessTransformers;
    }
}
