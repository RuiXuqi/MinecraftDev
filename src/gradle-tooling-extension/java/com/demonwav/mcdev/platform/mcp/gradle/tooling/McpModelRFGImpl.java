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

import java.io.File;
import java.util.List;
import java.util.Set;

final class McpModelRFGImpl implements McpModelRFG, java.io.Serializable {

    private final String minecraftVersion;
    private final String mcpVersion;
    private final Set<String> mappingFiles;
    private final List<File> accessTransformers;

    McpModelRFGImpl(
            String minecraftVersion,
            String mcpVersion,
            Set<String> mappingFiles,
            List<File> accessTransformers
    ) {
        this.minecraftVersion = minecraftVersion;
        this.mcpVersion = mcpVersion;
        this.mappingFiles = mappingFiles;
        this.accessTransformers = accessTransformers;
    }

    @Override
    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    @Override
    public String getMcpVersion() {
        return mcpVersion;
    }

    @Override
    public Set<String> getMappingFiles() {
        return mappingFiles;
    }

    @Override
    public List<File> getAccessTransformers() {
        return accessTransformers;
    }
}
