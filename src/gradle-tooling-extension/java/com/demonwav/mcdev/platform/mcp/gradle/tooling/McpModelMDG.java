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

import org.jetbrains.annotations.Nullable;

/**
 * Model for ModDevGradle's MCPForge plugin ({@code net.neoforged.moddev.mcpforge}), the MCP-based
 * legacy Forge/Cleanroom toolchain for Minecraft 1.12.2 and other legacy versions.
 */
public interface McpModelMDG {
    @Nullable String getMinecraftVersion();

    @Nullable String getPlatformVersion();

    @Nullable String getMcpVersion();

    @Nullable File getMappingsFile();

    List<File> getAccessTransformers();
}
