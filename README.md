# Openshift Ingest Processor Plugin for Elasticsearch

## Plugin objectives

The goal of this plugin is to forward specific documents into new indices while
making sure that the naming convention of those new indices (and their aliases)
are aligned with [Rollover API](https://www.elastic.co/guide/en/elasticsearch/reference/6.8/indices-rollover-index.html).

When a document is sent to `app-foo-write` index then this plugin check if there
is an index-alias of this name. If yes, then all is good, and the document is passed
without any modifications (this means the document will land in any index this
index-alias points to).
If, however, there is no such index-alias then this specific document is redirected
to index called `app-foo-000001`. If this index does not exist yet, Elasticsearch will
create it automatically. At the same time if there is such new index created then
this plugin check if this index has write-alias called `app-foo-write`
associated with it, if not then it will create such alias.

As a result log forwarders can send logs into any `*-write`endpoints and this plugin
will make sure that required new indices and relevant write-aliases are created under the hood
so that indices can be still correctly managed by Rollover API. 

To use this plugin you need to:
- Create a pipeline with this processor (see [integration test](tree/main/src/test/resources/rest-api-spec/test/ingest) cases for examples) 
- Add pipeline field into bulk request created by collectors
- Or create/modify index template and specify [dynamic setting](https://www.elastic.co/guide/en/elasticsearch/reference/6.8/index-modules.html#dynamic-index-settings) `index.default_pipeline`

### Why this is needed?

Prior to OpenShift Container Platform 4.5 (OCP) every OpenShift project
(equivalent of Kubernetes namespace) had its own Elasticsearch index for
logs. Starting with OCP 4.5 the data model for logs has been
changed to [store project logs into common/shared index](https://github.com/openshift/enhancements/blob/master/enhancements/cluster-logging/cluster-logging-es-rollover-data-design.md#data-model)
(the main motivation was to make the data model more scalable by reducing the number
of index shards).

However, when using common index then it is challenging to support
custom schema extensions. This means that documents stored in common index
must adhere to the same index mapping. Custom schema extensions lead to
incompatible field types, mapping collisions and explosions.

To enable custom schema extensions we designed a [proposal to utilized ingestion pipelines](https://github.com/openshift/enhancements/blob/master/enhancements/cluster-logging/cluster-logging-es-pipeline-processing.md)
to forward specific logs into new indices and this plugin is the implementation of ES part of that proposal.

## High-level plugin design

This plugin provides new ingestion processor that
update the value of the `_index` field for specific documents if needed. This means that
specific documents are "forwarded" to an extra (a schema specific) indices.

To support Rollover API we need to make sure that
there exists one (and only one) write-alias. Because it is not possible to directly create
requests to manipulate indices and their aliases from within the ingestion
processor this plugin provides another (action) plugin that subscribe to
cluster changes and on every cluster metadata update it check if there are
any indices that are missing the write-alias and create them.

Think of it as two independent plugins that are coordinated via
cluster metadata change events. This means that index creation and follow-up write-alias
creation/assignment is not an atomic operation. These are independent async. operations
that happen as fast as cluster metadata change event is able to spread through all nodes
in ES cluster.

### Alternative approach for ES > 7.10

Theoretically, it would be possible to replace the second action plugin with
an index template if the index templates supported `is_write_index: true` for aliases:

```
  - do:
      indices.put_template:
        include_type_name: false
        name: add_write_index_alias
        body:
          index_patterns: [app-*-00001]
          aliases:
            "{index}-write": { is_write_index: true }
            "{index}-read": {}
```
However, this is not supported in Elasticsearch < 7.11.

- https://github.com/elastic/elasticsearch/issues/52152#issuecomment-586748585 (ticker opened)
- https://github.com/elastic/elasticsearch/pull/52306 (community fix, closed)
- https://github.com/elastic/elasticsearch/pull/63162 (fix for ES 7.11 but license is no longer AL2)

Moreover, this approach does not give full control over aliases naming.

## Building instruction

This plugin uses gradle `esplugin` hence it requires specific versions
of both the JDK and Gradle that were used by target version
of Elasticsearch.

The gradle `esplugin` brings the following:
- [plugin descriptor file](https://www.elastic.co/guide/en/elasticsearch/plugins/6.8/plugin-authors.html#_plugin_descriptor_file) automation
- configurable integration [testing over REST API](https://github.com/elastic/elasticsearch/blob/v6.8.1/rest-api-spec/src/main/resources/rest-api-spec/test/README.asciidoc) (great collection of REST API integration tests can
be found [here](https://github.com/elastic/elasticsearch/blob/v6.8.6/rest-api-spec/src/main/resources/rest-api-spec/test/)). 

For Elasticsearch 6.8.1 it is the following which should be wrappered by gradlew:

- JDK12
- Gradle 5.5.1

Shell commands:

```shell
> gradlew clean
> gradlew check  # includes unit-testing
```
