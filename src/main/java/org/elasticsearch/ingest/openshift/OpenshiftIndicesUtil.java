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
import com.carrotsearch.hppc.cursors.ObjectObjectCursor;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.cluster.metadata.AliasOrIndex;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;

//import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class OpenshiftIndicesUtil {

    public static String generateInitialIndexName(final String schema) {
        // TODO[lvlcek]: Missing implementation of schema -> initial-index name transformation.
        return schema + "-00001";
    }

    public static String generateWriteAliasName(final String schema) {
        // TODO[lvlcek]: Missing implementation of schema -> write-index name transformation.
        return schema + "-write";
    }

    public static String extractSchemaFromIndex(final String index) {
        // TODO[lvlcek]: Missing implementation of index-name -> schema transformation.
        return index.trim();
    }

    /**
     * Helper method to create a simple Map of indices and corresponding alias metadata.
     * This Map is usually held locally as cache until it is replaced with a new version (which happens once
     * indices/aliases are updated in the cluster).
     *
     * @param indices Indices metadata taken from ClusterMetaData (eventState.metaData().getIndices())
     * @return Map of cluster indices
     */
    public static Map<String, Iterable<ObjectObjectCursor<String, AliasMetaData>>> getIndicesAndAliases(
            final ImmutableOpenMap<String, IndexMetaData> indices) {

        LinkedHashMap<String, Iterable<ObjectObjectCursor<String, AliasMetaData>>> metadata = new LinkedHashMap<>();

        for (ObjectObjectCursor<String, IndexMetaData> indexMD : indices) {
            metadata.put(indexMD.value.getIndex().getName(), indexMD.value.getAliases());
        }

        return metadata;
    }

    /**
     * Given the schema name this method returns is-write-alias for it iff it exists
     * in provided cache of indices/aliases.
     *
     * Return <code>null</code> if alias metadata with is-write-alias flag is not found.
     * This can happen because this alias hasn't been created yet or the alias is there but
     * for some reason the alias is not the "write alias".
     *
     * @param schema name of the schema
     * @param indices cache of indices
     * @return alias meta data
     */
    public static AliasMetaData getWriteIndexAlias(final String schema,
                                                   final Map<String, Iterable<ObjectObjectCursor<String, AliasMetaData>>> indices) {
        String writeAlias = generateWriteAliasName(schema);

        for (String index : indices.keySet()) {
            // TODO[lvlcek]: We can filter resulting set of indices using provided schema name
            // If index is not matching then:
            // continue;
            Iterable<ObjectObjectCursor<String, AliasMetaData>> aliases = indices.get(index);
            for (ObjectObjectCursor<String, AliasMetaData> alias : aliases) {
                if (writeAlias.equals(alias.value.alias()) && alias.value.writeIndex()) {
                    return alias.value;
                }
            }
        }

        return null;
    }

    /**
     *
     * @param indices Map of indices and aliases. This is expected to be the actual cache from Cluster State change event.
     * @return Names of indices that do not have one (and only one) write alias.
     */
    public static List<String> getIndicesWithoutWriteAlias(final Map<String, AliasOrIndex> indices) {
        return indices.entrySet().stream()
                .filter(x -> {
                        if(!x.getValue().isAlias()) {
                            IndexMetaData index = ((AliasOrIndex.Index)x.getValue()).getIndex();
                            // if ^^ breaks (because of ES version upgrade) then you can use x.getValue().getIndices().get(0)
                            int writeAliases = 0;
                            for (ObjectCursor<AliasMetaData> md: index.getAliases().values()) {
                                if (md.value.writeIndex()) {
                                    writeAliases += 1;
                                }
                            }
                            return writeAliases < 1;
                        } else {
                            return false;
                        }
                        })
                .map(x->x.getKey())
                .collect(Collectors.toList());
    }
}
