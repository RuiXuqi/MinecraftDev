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

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * Model for the Unimined Gradle plugin ({@code xyz.wagyourtail.unimined}).
 *
 * <p>Unimined is configured per source set, potentially with a different modloader for each
 * (e.g. a common {@code main} source set plus {@code fabric} and {@code neoforge} source sets
 * in a single Gradle project), so all data is carried per source set.
 */
public interface UniminedModel {

    List<SourceSetEntry> getSourceSets();

    interface SourceSetEntry {

        String getSourceSetName();

        String getMinecraftVersion();

        /**
         * Whether this source set uses a Fabric-like patcher (Fabric, Quilt, Flint, Babric…).
         */
        boolean isFabricLike();

        /**
         * Path to a Tiny v2 mappings file with intermediary and named namespaces.
         * Only set for Fabric-like patchers.
         */
        @Nullable
        File getTinyMappingsFile();

        /**
         * Path to an SRG-format mappings file laid out with named on the left and searge on the
         * right, matching the {@code mcp-srg.srg} layout StandardSrgParser expects for members.
         * Only set for Forge-like patchers.
         */
        @Nullable
        File getSrgMappingFile();

        List<File> getAccessTransformers();
    }
}
