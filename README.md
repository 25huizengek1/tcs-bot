# `tcs-discord-bot`

Discord bot that verifies members of unofficial TCS Discord servers.

## License

This code is released under the GPLv3 license. A copy is located at [LICENSE](LICENSE).

## Building

```shell
$ nix build .#
```

## Environment variables

- `DISCORD_ACCESS_TOKEN`: Discord Bot token (**required**)
- `CANVAS_ACCESS_TOKEN`: Canvas access token (**required**)
- `CANVAS_COURSE_CODE`: List of Canvas course codes (without the `course_`) (**required**)
- `CANVAS_BASE_URL`: (default: `https://canvas.utwente.nl`)
- `DATABASE_CONNECTION_STRING`: connection string for the SQlite database (default: `sqlite:///db.sqlite`)
- `MICROSOFT_CLIENT_ID`: client id used for Microsoft authentication (**required**)
- `MICROSOFT_CLIENT_SECRET`: client secret used for Microsoft authentication (**required**)
- `MICROSOFT_AUTH_ENDPOINT`: authentication endpoint used for Microsoft authentication (**required**)
- `HOST`: host the http server listens on (default: `0.0.0.0`)
- `PORT`: port the http server listens on (default: `6969`)
- `HOSTNAME`: the hostname used for (for example) redirect URI's (**required**)
- `ENVIRONMENT`: either `DEVELOPMENT` or `PRODUCTION` (default: `PRODUCTION`)
- `METRICS_PREFIX`: ip address prefix Prometheus metrics are available on (doesn't affect `DEVELOPMENT` environment) (default: "100")
