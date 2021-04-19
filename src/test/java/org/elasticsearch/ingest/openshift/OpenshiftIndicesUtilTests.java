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

import org.elasticsearch.Version;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.cluster.metadata.AliasOrIndex;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.test.ESTestCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.elasticsearch.ingest.openshift.OpenshiftIndicesUtil.generateInitialIndexName;
import static org.elasticsearch.ingest.openshift.OpenshiftIndicesUtil.generateWriteAliasName;
import static org.elasticsearch.ingest.openshift.OpenshiftIndicesUtil.hasDataModelPrefix;

public class OpenshiftIndicesUtilTests extends ESTestCase {

    public void testGenerateInitialIndexName() {
        assertEquals("app-000001", generateInitialIndexName("app-write"));
        assertEquals("alias-write-000001", generateInitialIndexName("alias-write-write"));
        assertEquals("-000001", generateInitialIndexName("-write"));
    }

    public void testGenerateWriteAliasName() {
        assertEquals("app-write", generateWriteAliasName("app-000001"));
        assertEquals("foo-write-write", generateWriteAliasName("foo-write-000001"));
        assertEquals("-write", generateWriteAliasName("-000001"));
    }

    public void testDataModelPrefix() {
        for (String prefix : Arrays.asList("app", "infra", "audit")) {

            assertFalse(hasDataModelPrefix("." + prefix));
            assertFalse(hasDataModelPrefix(prefix));


            assertTrue(hasDataModelPrefix(prefix + "-"));
            assertTrue(hasDataModelPrefix(prefix + "-foo"));
        }
    }

    public void testIndicesWithoutWriteIndex() {
        List<String> indices = OpenshiftIndicesUtil.getInitialIndicesWithoutWriteAlias(Collections.emptyMap());
        assertTrue(indices.isEmpty());

        Map<String, AliasOrIndex> im = new TreeMap<>();
        im.put("app-index1-000001", new AliasOrIndex.Index(createIndexMetaData("app-index1-000001")));
        im.put("app-index2-000001", new AliasOrIndex.Index(createIndexMetaData("app-index2-000001",
                new AliasInfo("app-alias1", false),
                new AliasInfo("app-alias2", false))));
        im.put("app-index3-000001", new AliasOrIndex.Index(createIndexMetaData("app-index3-000001",
                new AliasInfo("app-alias1", true))));
        im.put("app-index4-000001", new AliasOrIndex.Index(createIndexMetaData("app-index4-000001",
                new AliasInfo("app-alias1", true),
                new AliasInfo("app-alias2", true))));

        im.put("app-alias1", new AliasOrIndex.Alias(createAliasMetaData("alias1"), createIndexMetaData("app-index1-000001")));

        indices = OpenshiftIndicesUtil.getInitialIndicesWithoutWriteAlias(im);

        assertEquals(indices.size(), 2);
        assertTrue(indices.contains("app-index1-000001"));
        assertTrue(indices.contains("app-index2-000001"));

        im = new TreeMap<>();
        im.put("app-index1-000001", new AliasOrIndex.Index(createIndexMetaData("app-index1-000001")));
        im.put("app-index2-000002", new AliasOrIndex.Index(createIndexMetaData("app-index2-000002",
                new AliasInfo("app-alias1", false),
                new AliasInfo("app-alias2", false))));
        im.put("app-index3-000003", new AliasOrIndex.Index(createIndexMetaData("app-index3-000003",
                new AliasInfo("app-alias1", true))));

        indices = OpenshiftIndicesUtil.getInitialIndicesWithoutWriteAlias(im);

        assertEquals(indices.size(), 1);
        assertTrue(indices.contains("app-index1-000001"));
    }

    private AliasMetaData createAliasMetaData(String alias) {
        AliasMetaData.Builder amBuilder = AliasMetaData.builder(alias);
        return amBuilder.build();
    }

    private IndexMetaData createIndexMetaData(String index, AliasInfo ... alias) {
        IndexMetaData.Builder imBuilder = IndexMetaData.builder(index)
                .settings(Settings.builder().put("index.version.created", Version.CURRENT)) //required(*);
                .numberOfShards(3)   //required
                .numberOfReplicas(1); //required
        // (*) setting of 'index.version.created' must be done before numberOfShards is called, or the test
        // fails: Throwable #1: java.lang.IllegalArgumentException: must specify numberOfShards for index [___]

        for (AliasInfo ai : alias) {
            imBuilder.putAlias(AliasMetaData.builder(ai.alias).writeIndex(ai.writeAlias).build());
        }

        return imBuilder.build();
    }

    private class AliasInfo {
        public final String alias;
        public final Boolean writeAlias;
        AliasInfo(String alias, Boolean writeAlias) {
            this.alias = alias;
            this.writeAlias = writeAlias;
        }
    }
}
