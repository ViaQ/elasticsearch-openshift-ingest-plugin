# Openshift Ingest Processor Plugin for Elasticsearch

**This is still POC WIP.**

### Plugin objectives

The goal is to enable forwarding of specific documents from within ingestion processor
to (custom) schema based indices.

Prior to OpenShift Container Platform 4.5 (OCP) every OpenShift project
(equivalent of Kubenetes namespace) had its own Elasticsearch index for
logs. Starting with OCP 4.5 the data model for logs has been changed to
store project logs into common/shared index.

However, when using common index then it is challenging to support
custom schema extensions. Shortly, documents stored in common index
must adhere to the same index mapping. Custom extensions lead to
incompatible field types, mapping collisions and explosions.

### High-level plugin design

This plugin provides new ingestion processor that
update the value of the `_index` field for specific documents. This means that
specific documents are "forwarded" to an extra (a schema specific) indices.

To support rollover API we need to make sure that
there exists one write-alias. Because it is not possible to directly create
requests to manipulate indices and their aliases from within the ingestion
processor this plugin provide another (action) plugin that subscribe to
cluster changes and on every cluster metadata update it check if there are
any schema driven indices that are missing write-alias and create them.

Think of it as two independent plugins that coordinate in async manner via
cluster state change events.

### Building instruction

This plugin uses gradle `esplugin` hence it requires specific versions
of both the JDK and Gradle that were used by target version
of Elasticsearch.

The gradle `esplugin` brings the following:
- [plugin descriptor file](https://www.elastic.co/guide/en/elasticsearch/plugins/6.8/plugin-authors.html#_plugin_descriptor_file) automation
- configurable integration [testing over REST API](https://github.com/elastic/elasticsearch/blob/v6.8.6/rest-api-spec/src/main/resources/rest-api-spec/test/README.asciidoc)

For Elasticsearch 6.8.6 it is:

- JDK12
- Gradle 5.5.1

Shell commands:

```shell
> gradle clean
> gradle check  # includes unit-testing
```
