#!/usr/bin/env node
import { createService } from '../app/service.js';
import { configFromEnv } from '../config/config.js';
import { buildServer } from '../http/server.js';
import { loadJob } from '../store/fileStore.js';

const config = configFromEnv(process.env);
const service = createService(await loadJob(config.jobFile), config);
const app = buildServer(service);

/**
 * Shut down on a signal rather than being killed.
 *
 * The server holds no writable state, but a request part way through building a
 * certificate is doing a lot of arithmetic, and letting it finish keeps the
 * access log honest about what was actually served.
 */
for (const signal of ['SIGINT', 'SIGTERM'] as const) {
  process.on(signal, () => {
    void app.close().then(() => {
      process.exit(0);
    });
  });
}

await app.listen({ host: config.http.host, port: config.http.port });
process.stderr.write(
  `chainageworks serving ${config.jobFile} on ${config.http.host}:${String(config.http.port)}\n`,
);
