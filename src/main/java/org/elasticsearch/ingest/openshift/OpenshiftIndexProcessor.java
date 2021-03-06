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

import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateListener;
import org.elasticsearch.cluster.metadata.AliasOrIndex;
import org.elasticsearch.ingest.AbstractProcessor;
import org.elasticsearch.ingest.IngestDocument;
import org.elasticsearch.ingest.IngestService;
import org.elasticsearch.ingest.Processor;
import org.elasticsearch.rest.action.admin.indices.AliasesNotFoundException;

import java.util.Collections;
import java.util.Map;

import static org.elasticsearch.ingest.openshift.OpenshiftIndicesUtil.generateInitialIndexName;
import static org.elasticsearch.ingest.openshift.OpenshiftIndicesUtil.hasDataModelPrefix;

/**
 * Openshift ingestion processor modify the "_index" value of incoming document. The function of this processor
 * is to "redirect" the schema driven document either to a new index or to existing write-alias for existing index.
 *
 * Notice Elasticsearch can instantiate several instances of this plugin per single ES node.
 *
 * Every instance of this plugin keeps its own lookup table of latest indices/aliases.
 */
public final class OpenshiftIndexProcessor extends AbstractProcessor {

    public static final String TYPE = "openshift-ingestion-processor";

    // Local cache of known indices and their aliases
    private Map<String, AliasOrIndex> latestAliasAndIndicesLookup;
    private long clusterStateVersion = Long.MIN_VALUE;

    OpenshiftIndexProcessor(final String tag, final IngestService ingestService) {
        super(tag);

        // Init the internal indices map.
        this.latestAliasAndIndicesLookup = Collections.emptyMap();

        // ClusterEventListener is able to listen to changes in ClusterState; by this we listen to changes
        // in indices and their aliases.
        ingestService.getClusterService().addListener(csl);
    }

    private ClusterStateListener csl = event -> {
        // We are interested only in index and its aliases changes. This is part of Cluster MetaData.
        if (!event.metaDataChanged()) {
            return;
        }

        ClusterState eventState = event.state();

        synchronized (this) {
            if (eventState.version() > clusterStateVersion) {
                latestAliasAndIndicesLookup = eventState.metaData().getAliasAndIndexLookup();
                clusterStateVersion = eventState.version();
            }
        }

    };

    @Override
    public IngestDocument execute(IngestDocument ingestDocument) {

        String aliasName = ingestDocument.getFieldValue("_index", String.class, Boolean.TRUE);

        // We assume that the forwarder will always send documents to the write-alias.
        // Skip everything that does not seem to be part of data model.
        if (aliasName != null && hasDataModelPrefix(aliasName) && aliasName.endsWith("-write")) {
            // If the write-alias is not known yet (does not exist) then
            // we change the target to be the initial index
            if (!latestAliasAndIndicesLookup.containsKey(aliasName)) {
                String index = generateInitialIndexName(aliasName);
                ingestDocument.setFieldValue("_index", index);
            } else {
                // What to do if the write-alias target exists but it is not actually an alias?
                if (!latestAliasAndIndicesLookup.get(aliasName).isAlias()) {
                    // This needs to be discussed.
                    throw new AliasesNotFoundException(aliasName);
                }
            }
        }

        return ingestDocument;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public static final class Factory implements Processor.Factory {

        private final IngestService ingestService;

        public Factory(IngestService ingestService) {
            this.ingestService = ingestService;
        }

        @Override
        public Processor create(Map<String, Processor.Factory> registry, String processorTag, Map<String, Object> config) throws Exception {
            return new OpenshiftIndexProcessor(processorTag, ingestService);
        }
    }
}
