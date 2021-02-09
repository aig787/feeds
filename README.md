Feeds
=====
![tag](https://img.shields.io/github/v/tag/devoinc/feeds)

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

| Class | Description |
|-------|-------------|
| com.devo.feeds.output.LoggingAttributeOutput | Log attributes to stdout |
| com.devo.feeds.output.SyslogAttributeOutput | Send attributes to syslog<br>Configuration:<br><ul><li>host (required): Syslog server</li><li>port (required): Syslog port</li><li>tags (required: e.g. ['misp.feed']): List of syslog tags, attributes will be sent with each<li>chain (optional): Syslog TLS certificate </li><li>keystore (optional): Client TLS keystore</li><li>keystorePass (optional): Keystore password</li><li>threads (default: 1): Number of output threads</li></ul> |
| com.devo.feeds.output.DevoAttributeOutput | Send attributes to Devo<br>Configuration:<br><ul><li>host (required): Devo Relay FQDN</li><li>port (required): Devo Relay port</li><li>chain (required): Devo Relay chain cert</li><li>keystore (required): Devo keystore</li><li>keystorePass (required): Devo keystore password</li><li>threads (default: 1): Number of output threads</li></ul> |

Caches
------

| Class | Description |
|-------|-------------|
| com.devo.feeds.storage.InMemoryAttributeCache | Cache attributes in memory, cache is lost on restarts |
| com.devo.feeds.storage.FilesystemAttributeCache | Cache attributes on disk<br>Configuration:<br><ul><li>path (required): Directory to store local cache</li></ul> |

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
    class = com.devo.feeds.storage.FilesystemAttributeCache
    path = /tmp/feeds
  }
  outputs: [
    {
      class = com.devo.feeds.output.LoggingAttributeOutput
    },
    {
      class = com.devo.feeds.output.DevoAttributeOutput
      host = usa.elb.relay.logtrust.net
      port = 443
      chain = chain.crt
      keystore = devo-keystore.pkcs12
      keystorePass = changeme
    }
  ]
}
```
