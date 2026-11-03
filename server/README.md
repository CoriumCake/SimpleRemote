# SimpleRemote stats server

Receives the app's opt-in anonymous "this code worked / didn't" results
(see `app/src/main/java/com/simpleremote/data/Stats.kt`). Cloudflare Worker + D1, fits the free plan.

```sh
cd server
npx wrangler login
npx wrangler d1 create simpleremote-stats        # copy database_id into wrangler.toml
npx wrangler d1 execute simpleremote-stats --remote --file schema.sql
npx wrangler secret put STATS_READ_KEY           # any long random string
npx wrangler deploy                              # prints https://simpleremote-stats.<you>.workers.dev
```

Build the app with the endpoint:

```sh
./gradlew assembleDebug -PstatsUrl=https://simpleremote-stats.<you>.workers.dev/v1/events
```

Read results: `https://simpleremote-stats.<you>.workers.dev/v1/codes?key=<STATS_READ_KEY>&cat=tv&brand=LG`
