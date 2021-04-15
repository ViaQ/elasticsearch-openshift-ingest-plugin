/*
 * Copyright 2021 Lukáš Vlček
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.elasticsearch.ingest.openshift;

import com.carrotsearch.hppc.cursors.ObjectCursor;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.cluster.metadata.AliasOrIndex;
import org.elasticsearch.cluster.metadata.IndexMetaData;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class OpenshiftIndicesUtil {

    /**
     * Replaces ending "-write" with "-00001".
     * @param aliasName assume write-alias
     * @return initial index name
     */
    public static String generateInitialIndexName(final String aliasName) {
        return aliasName.replaceAll("-write$", "-00001");
    }

    /**
     * Replaces ending "-00001" with "-write".
     * @param index assume initialIndex
     * @return write-alias
     */
    public static String generateWriteAliasName(final String index) {
        return index.replaceAll("-00001$", "-write");
    }

    public static boolean isInitialIndex(final String index) {
        return index.endsWith("-00001");
    }

    /**
     *
     * @param indices Map of indices and aliases. This is expected to be the actual cache from Cluster State change event.
     * @return Names of initial indices that have no write-alias.
     */
    public static List<String> getInitialIndicesWithoutWriteAlias(final Map<String, AliasOrIndex> indices) {
        return indices.entrySet().stream()
                .filter(x -> {
                        if(!x.getValue().isAlias()) {
                            IndexMetaData imd = ((AliasOrIndex.Index)x.getValue()).getIndex();
                            // if ^^ breaks (because of ES version upgrade) then you can use x.getValue().getIndices().get(0)
                            if (!isInitialIndex(imd.getIndex().getName())) return false;
                            for (ObjectCursor<AliasMetaData> md: imd.getAliases().values()) {
                                if (md.value.writeIndex()) {
                                    return false;
                                }
                            }
                            return true;
                        }
                        return false;
                        })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
