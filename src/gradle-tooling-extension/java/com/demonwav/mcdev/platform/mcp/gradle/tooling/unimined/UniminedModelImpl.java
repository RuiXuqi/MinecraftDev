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

final class UniminedModelImpl implements UniminedModel, java.io.Serializable {

    private final List<SourceSetEntry> sourceSets;

    UniminedModelImpl(List<SourceSetEntry> sourceSets) {
        this.sourceSets = sourceSets;
    }

    @Override
    public List<SourceSetEntry> getSourceSets() {
        return sourceSets;
    }

    static final class SourceSetEntryImpl implements SourceSetEntry, java.io.Serializable {

        private final String sourceSetName;
        private final String minecraftVersion;
        private final boolean fabricLike;
        private final @Nullable File tinyMappingsFile;
        private final @Nullable File srgMappingFile;
        private final List<File> accessTransformers;

        SourceSetEntryImpl(
                String sourceSetName,
                String minecraftVersion,
                boolean fabricLike,
                @Nullable File tinyMappingsFile,
                @Nullable File srgMappingFile,
                List<File> accessTransformers
        ) {
            this.sourceSetName = sourceSetName;
            this.minecraftVersion = minecraftVersion;
            this.fabricLike = fabricLike;
            this.tinyMappingsFile = tinyMappingsFile;
            this.srgMappingFile = srgMappingFile;
            this.accessTransformers = accessTransformers;
        }

        @Override
        public String getSourceSetName() {
            return sourceSetName;
        }

        @Override
        public String getMinecraftVersion() {
            return minecraftVersion;
        }

        @Override
        public boolean isFabricLike() {
            return fabricLike;
        }

        @Override
        public @Nullable File getTinyMappingsFile() {
            return tinyMappingsFile;
        }

        @Override
        public @Nullable File getSrgMappingFile() {
            return srgMappingFile;
        }

        @Override
        public List<File> getAccessTransformers() {
            return accessTransformers;
        }
    }
}
