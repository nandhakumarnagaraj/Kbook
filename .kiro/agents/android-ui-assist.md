# Android UI Assist Agent

This agent provides Android UI assistance for the KhanaBook project via the android-ui-assist MCP server.

## MCP Servers

- android-ui-assist: Provides tools for Android UI development, component suggestions, layout validation, and Material Design guidance.

## Configuration

The MCP server config is located at: `mcp/android-ui-mcp.json`

```json
{
  "mcpServers": {
    "android-ui-assist": {
      "command": "npx",
      "args": ["android-ui-assist-mcp"],
      "timeout": 10000
    }
  }
}
```
