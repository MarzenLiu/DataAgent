# Local Milvus

The Java services require a network endpoint on port `19530`. Milvus Lite is an
embedded Python library, so the local Docker setup uses the lightweight,
single-container Milvus Standalone deployment instead.

## Start and verify

```bash
docker compose -f docker-file/docker-compose-dev.yml up -d milvus
docker compose -f docker-file/docker-compose-dev.yml ps milvus
curl http://127.0.0.1:19091/healthz
```

Milvus listens on `127.0.0.1:19530`. Its health and WebUI port is
`127.0.0.1:19091`; port `9091` is intentionally not exposed because it is used
by the local Langfuse stack.

The persistent data is stored in the Docker volume
`data-agent-dev_milvus-data`.

## Application configuration

The management service uses the `milvus` profile by default and writes Spring
AI documents to the `default.vector_store` collection. AgentScope reads the
same collection directly with the official Milvus Java SDK; its module does not
depend on Spring AI or Spring AI Alibaba.

Useful overrides:

```bash
MILVUS_HOST=127.0.0.1
MILVUS_PORT=19530
MILVUS_DATABASE=default
MILVUS_COLLECTION=vector_store
MILVUS_DIMENSION=1024
```

To run the management service with a different profile, set
`SPRING_PROFILES_ACTIVE`. To temporarily fall back to the JSON store in the
AgentScope service, set `AGENTSCOPE_RAG_STORE_TYPE=simple`.

## Stop

```bash
docker compose -f docker-file/docker-compose-dev.yml stop milvus
```

Stopping the container preserves the volume. Do not run `down -v` unless the
local vectors should be deleted.
