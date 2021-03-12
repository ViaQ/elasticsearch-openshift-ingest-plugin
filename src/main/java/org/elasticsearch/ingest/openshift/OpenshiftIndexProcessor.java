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

import org.elasticsearch.ingest.AbstractProcessor;
import org.elasticsearch.ingest.IngestDocument;
import org.elasticsearch.ingest.Processor;

import java.util.Map;

public final class OpenshiftIndexProcessor extends AbstractProcessor {

    public static final String TYPE = "openshift-index_name-processor";

    OpenshiftIndexProcessor(String tag) {
        super(tag);
    }

    @Override
    public IngestDocument execute(IngestDocument ingestDocument) {
        return ingestDocument;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public static final class Factory implements Processor.Factory {

        @Override
        public Processor create(Map<String, Processor.Factory> registry, String processorTag, Map<String, Object> config) throws Exception {
            return new OpenshiftIndexProcessor(processorTag);
        }
    }
}
