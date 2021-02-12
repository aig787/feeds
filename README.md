Feeds
=====
![tag](https://img.shields.io/github/v/tag/aig787/feeds)

Fetch indicators from feeds in various formats to push towards the storage of your choice.

Requirements
------------

Feeds requires JDK 11 or greater

Running
-------

## Docker

CLI

```bash
docker run -v $CONF:/opt/sightingdb/conf/application.conf aig787/feeds:$VERSION
```

Compose

```yaml
version: "3"
services:
  feeds:
    build: .
    volumes:
      - $CONF:/opt/sightingdb/conf/application.conf
```

### Gradle

Run `./gradlew run` from the project root directory

Outputs
-------

### Included Outputs

| Class | Description |
|-------|-------------|
| com.devo.feeds.output.LoggingOutputFactory | Log attributes to stdout |
| com.devo.feeds.output.SyslogOutputFactory | Send attributes to syslog<br>Configuration:<br><ul><li>host (required): Syslog server</li><li>port (required): Syslog port</li><li>tags (required: e.g. ['misp.feed']): List of syslog tags, attributes will be sent with each<li>chain (optional): Syslog TLS certificate </li><li>keystore (optional): Client TLS keystore</li><li>keystorePass (optional): Keystore password</li><li>threads (default: 1): Number of output threads</li></ul> |
| com.devo.feeds.output.DevoOutputFactory | Send attributes to Devo<br>Configuration:<br><ul><li>host (required): Devo Relay FQDN</li><li>port (required): Devo Relay port</li><li>chain (required): Devo Relay chain cert</li><li>keystore (required): Devo keystore</li><li>keystorePass (required): Devo keystore password</li><li>threads (default: 1): Number of output threads</li></ul> |
| com.devo.feeds.output.KafkaOutputFactory | Send attributes and events to Kafka<br>Configuration<br><ul><li>eventTopic (required): Topic where events will be written</li><li>attributeTopic (required): Topic where attributes will be written</li><li>properties (required): Map of [Kafka producer configs](https://kafka.apache.org/documentation/#producerconfigs). Must include `bootstrap.servers`

### Custom Outputs

Arbitrary outputs can be configured if they are present on the classpath. Custom outputs require a `fromConfig` method
that takes a [typesafe config](https://github.com/lightbend/config) as input and returns a subclass
of [Output](output/src/main/kotlin/com/devo/feeds/output/Output.kt). See
the [Kafka output](output/src/main/kotlin/com/devo/feeds/output/KafkaOutput.kt) for an example.

Caches
------

### Included Caches

| Class | Description |
|-------|-------------|
| com.devo.feeds.storage.InMemoryAttributeCacheFactory | Cache attributes in memory, cache is lost on restarts |
| com.devo.feeds.storage.FilesystemAttributeCacheFactory | Cache attributes on disk<br>Configuration:<br><ul><li>path (required): Directory to store local cache</li></ul> |

### Custom Caches

Arbitrary caches can be configured if they are present on the classpath. Custom caches require a `fromConfig` method
that takes a [typesafe config](https://github.com/lightbend/config) as input and returns a subclass
of [AttributeCache](storage/src/main/kotlin/com/devo/feeds/storage/AttributeCache.kt).

Configuration
-------------

[Reference configuration](src/main/resources/reference.conf)

Example configuration

```hocon
feeds {
  misp {
    url = "https://localhost:4433"
  }
  # Number of http client threads
  http.client.threads = 5
  # How often to update feeds
  feedUpdateInterval = "1 hour"
  # How often to check config in MISP
  mispUpdateInterval = "1 hour"
  cache {
    class = com.devo.feeds.storage.FilesystemAttributeCacheFactory
    path = /tmp/feeds
  }
  outputs: [
    {
      class = com.devo.feeds.output.LoggingOutputFactory
    },
    {
      class = com.devo.feeds.output.DevoAttributeOutputFactory
      host = usa.elb.relay.logtrust.net
      port = 443
      chain = chain.crt
      keystore = devo-keystore.pkcs12
      keystorePass = changeme
    }
  ]
}
```
