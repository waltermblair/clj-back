# gback

Web server demo (see https://github.com/waltermblair/cljs-front).

Publishes Docker image from "release" branch

## Usage

```
lein test
```

```
docker-compose up

curl localhost:3000/health
curl localhost:3000/api/1/marketplace
```