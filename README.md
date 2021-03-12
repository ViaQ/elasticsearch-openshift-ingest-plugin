# Openshift Ingest Processor Plugin for Elasticsearch

### Processor description

This plugin is early WIP.

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
