# Trusted MQTT client credentials

The EMQX extension is shared by multiple device integrations. It only enables the bypass when a deployment explicitly configures one trusted internal MQTT-client identity in `repository/conf/deployment.toml`:

```toml
[emqx.trusted_client]
username = "$env{AMI_NODE_MQTT_USERNAME}"
password = "$env{AMI_NODE_MQTT_PASSWORD}"
```

An AMI deployment can supply its node credentials through this optional section. The exhook treats only an exact username/password match as trusted, skips introspection for that connection, and bypasses its scope-based ACL check for the authenticated client session. All other clients use the normal token and scope flow. If this section is absent, no bypass is enabled.

Add the same username to EMQX's authorization configuration with only the topics required by that integration. For example, an AMI deployment can grant its dedicated username access to its AMI topic namespace:

```
{allow, {username, "ami-node"}, all, ["ami/#"]}.
```

Use the username configured above in place of `ami-node` and restrict the topic filter to the AMI topics used by the deployment.
