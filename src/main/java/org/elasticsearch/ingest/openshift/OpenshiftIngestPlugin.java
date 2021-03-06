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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequestBuilder;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterChangedEvent;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateListener;
import org.elasticsearch.cluster.metadata.AliasOrIndex;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.NodeEnvironment;
import org.elasticsearch.ingest.Processor;
import org.elasticsearch.plugins.ClusterPlugin;
import org.elasticsearch.plugins.IngestPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.watcher.ResourceWatcherService;

import static org.elasticsearch.ingest.openshift.OpenshiftIndicesUtil.getInitialIndicesWithoutWriteAlias;
import static org.elasticsearch.ingest.openshift.OpenshiftIndicesUtil.generateWriteAliasName;

/**
 * The purpose of this plugin is to investigate changes in indices and aliases (taken from cluster meta data updates)
 * and identify schema indices that are missing the write-alias flag. If there are such indices it creates the
 * Index Aliases request to handle it.
 * See: https://www.elastic.co/guide/en/elasticsearch/reference/6.8/indices-aliases.html
 */
public class OpenshiftIngestPlugin extends Plugin implements IngestPlugin, ClusterPlugin {

    private static final Logger logger = LogManager.getLogger(OpenshiftIngestPlugin.class);

    // Local cache of known indices and their aliases
    private Map<String, AliasOrIndex> latestAliasAndIndicesLookup;
    private long clusterStateVersion = Long.MIN_VALUE;

    @Override
    public Map<String, Processor.Factory> getProcessors(Processor.Parameters parameters) {
        return Collections.singletonMap(OpenshiftIndexProcessor.TYPE, new OpenshiftIndexProcessor.Factory(
                parameters.ingestService
        ));
    }

    @Override
    public Collection<Object> createComponents(Client client, ClusterService clusterService, ThreadPool threadPool,
                                               ResourceWatcherService resourceWatcherService, ScriptService scriptService,
                                               NamedXContentRegistry xContentRegistry, Environment environment,
                                               NodeEnvironment nodeEnvironment, NamedWriteableRegistry namedWriteableRegistry) {

        // Init the internal indices map.
        this.latestAliasAndIndicesLookup = Collections.emptyMap();

        clusterService.addListener(new IndicesUpdatedListener(client));
        return Collections.emptyList();
    }

    class IndicesUpdatedListener implements ClusterStateListener {

        private Client client;

        IndicesUpdatedListener(Client client) {
            this.client = client;
        }

        @Override
        public void clusterChanged(ClusterChangedEvent event) {
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

            /*
             Any modification of index metadata (including adding index aliases) is done on the master node
             hence we proceed only if the local node is the master node.

             Question: Can it happen that we miss on creating required index alias(es)
             because the master node is re-election in the meantime?
             Maybe we could workaround this by scheduling this task once a while...?
             */
            if (event.localNodeMaster()) {

                List<String> indices = getInitialIndicesWithoutWriteAlias(latestAliasAndIndicesLookup);
                if (!indices.isEmpty()) {
                    IndicesAliasesRequestBuilder iarb = client.admin().indices().prepareAliases();

                    for (String index: indices) {

                        String writeAlias = generateWriteAliasName(index);
                        // Initial indices that were already rolled-over will not have write alias. We need to skip them.
                        // In other words the writeAlias already exists (perhaps pointed to "-000002" index or older).
                        if (!writeAlias.isEmpty() && !latestAliasAndIndicesLookup.containsKey(writeAlias)) {
                            iarb.addAliasAction(AliasActions.add()
                                    .index(index)
                                    .alias(writeAlias)
                                    .writeIndex(true));
                            logger.trace("Prepared write index alias {} request for index {}", writeAlias, index);
                        }
                    }

                    IndicesAliasesRequest iar = iarb.request();
                    if (!iar.getAliasActions().isEmpty()) {
                        client.admin().indices().aliases(iar, new ActionListener<AcknowledgedResponse>() {
                            @Override
                            public void onResponse(AcknowledgedResponse acknowledgedResponse) {
                                logger.debug("Write aliases added for the following indices: {}", indices);
                            }

                            @Override
                            public void onFailure(Exception e) {
                                // TODO[lvlcek]: If there is an exception like "Alias already exists" then we can ignore it.
                                // However, if the index create request fails because the client does not have appropriate credentials
                                // or any other serious reason then we need to escalate it.
                                logger.warn("Error occurred when adding write aliases for the following indices: {}. {}", indices, e);
                            }
                        });
                    }
                }
            }
        }
    }
}