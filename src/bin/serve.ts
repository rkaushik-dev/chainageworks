#!/usr/bin/env node
import { createService } from '../app/service.js';
import { startupReport, startupSummary } from '../app/startup.js';
import { configFromEnv } from '../config/config.js';
import { buildServer } from '../http/server.js';
import { loadJob } from '../store/fileStore.js';

const config = configFromEnv(process.env);
const service = createService(await loadJob(config.jobFile), config);

/**
 * Say what is being served before serving it.
 *
 * A server that starts silently on the wrong job file is the single easiest
 * mistake to make here, because the file comes from an environment variable
 * and the failure looks like a valuation that is quietly out by a month.
 */
const startup = startupReport(service, config);
for (const line of startup.lines) process.stderr.write(`${line}\n`);
process.stderr.write(`${startupSummary(startup)}\n`);

if (startup.refuses) {
  process.exitCode = 1;
} else {
  const app = buildServer(service);

  /**
   * Shut down on a signal rather than being killed.
   *
   * The server holds no writable state, but a request part way through building
   * a certificate is doing a lot of arithmetic, and letting it finish keeps the
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
}
