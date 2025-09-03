import Fastify from 'fastify';
import type { FastifyInstance } from 'fastify';
import { BadInputError } from '../errors.js';
import { billAsAt } from '../domain/bill/revision.js';
import { formatItemCode } from '../domain/bill/itemCode.js';
import { formatChainageRange } from '../domain/chainage/range.js';
import { formatQuantity } from '../domain/quantity/quantity.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import { formatSiteDate, tryParseSiteDate } from '../domain/time/siteDate.js';
import { rateMajor } from '../domain/money/rate.js';
import { diagnose, isHealthy } from '../app/doctor.js';
import type { Service } from '../app/service.js';
import {
  cashFlowFor,
  certificateFor,
  contextOf,
  curveFor,
  defectsFor,
  delayFor,
  loadApplications,
  loadDiary,
  loadIndices,
  loadNcrs,
  loadNotices,
  loadPayLessNotices,
  haulPlan,
  lookaheadFrom,
  outputFor,
  outturnFor,
  revisionsFor,
  risksFor,
  variationsFor,
  loadRecords,
  loadResourceReturns,
  loadSignOffs,
  paymentsFor,
  progressFor,
  scheduleOf,
  stripFor,
} from '../app/service.js';
import { effectiveRecords } from '../domain/survey/record.js';
import { jobBlockers } from '../store/job.js';
import {
  cashFlowDto,
  certificateDto,
  curveDto,
  defectsReportDto,
  delayReportDto,
  importReportDto,
  haulPlanDto,
  lookaheadDto,
  moneyDto,
  outputReportDto,
  outturnDto,
  revisionReportDto,
  riskPositionDto,
  variationReportDto,
  periodDto,
  progressReportDto,
  stripDto,
  subcontractReportDto,
} from './dto.js';
import type { ImportReport } from '../ingest/report.js';
import { toFailure } from './problem.js';

function numberParam(raw: unknown, what: string): number {
  if (typeof raw !== 'string') {
    throw new BadInputError(`${what} has to be a whole number from one`, { what });
  }
  const value = Number(raw);
  if (!Number.isInteger(value) || value < 1) {
    throw new BadInputError(`${what} has to be a whole number from one`, { value: raw });
  }
  return value;
}

/** A query parameter that counts from zero, so nothing at all is allowed. */
function fromZeroQuery(raw: unknown, what: string): number {
  if (raw === undefined) return 0;
  if (typeof raw !== 'string') {
    throw new BadInputError(`${what} has to be a whole number from zero`, { what });
  }
  const value = Number(raw);
  if (!Number.isInteger(value) || value < 0) {
    throw new BadInputError(`${what} has to be a whole number from zero`, { value: raw });
  }
  return value;
}

/** A query parameter that counts, with a default when it is absent. */
function countQuery(raw: unknown, fallback: number, what: string): number {
  return raw === undefined ? fallback : numberParam(raw, what);
}

function dateQuery(raw: unknown, fallback: SiteDate): SiteDate {
  if (typeof raw !== 'string' || raw === '') return fallback;
  const parsed = tryParseSiteDate(raw);
  if (!parsed.ok) throw parsed.error;
  return parsed.value;
}

/**
 * The read mostly API.
 *
 * Everything that reads takes an as-at date or a valuation number, and nothing
 * defaults to today. A report that quietly means "now" cannot be compared
 * against the one somebody printed last week, and comparing those two is most
 * of what this API is used for.
 */
export function buildServer(service: Service): FastifyInstance {
  const app = Fastify({
    logger: false,
    // A site export is the largest thing anybody posts, and a large one is
    // eight thousand rows. Two megabytes leaves room for that and refuses the
    // request that is actually somebody's photo of a site diary.
    bodyLimit: 2 * 1024 * 1024,
    // A csv import names one register, so a long path is a mistake rather
    // than a request. Refusing it here keeps the router from walking it.
    maxParamLength: 200,
  });

  /**
   * Every response carries the request id it was answered under.
   *
   * Fastify generates one anyway; putting it on the response is what makes it
   * useful, because the only time anybody wants it is when they are quoting a
   * failure back and the log is the other half of the story.
   */
  app.addHook('onSend', (request, reply, payload, done) => {
    void reply.header('x-request-id', request.id);
    done(null, payload);
  });

  app.addContentTypeParser(
    ['text/csv', 'text/plain'],
    { parseAs: 'string' },
    (_request, body, done) => {
      done(null, body);
    },
  );

  app.setErrorHandler((error, _request, reply) => {
    const failure = toFailure(error);
    void reply.status(failure.status).send(failure.body);
  });

  /**
   * A path this server knows reached with a method it does not is a 405 with
   * the methods that would have worked, rather than a 404. The difference
   * matters to a client that is one line of code away from working.
   */
  app.setNotFoundHandler((request, reply) => {
    const allowed = methodsFor(app, request.url);
    if (allowed.length > 0) {
      void reply
        .status(405)
        .header('allow', allowed.join(', '))
        .send({
          code: 'unsupported',
          message: `that route answers ${allowed.join(', ')}`,
          details: { method: request.method, url: request.url },
        });
      return;
    }
    void reply.status(404).send({
      code: 'not_found',
      message: 'no such route',
      details: {},
    });
  });

  app.get('/health', () => ({ status: 'ok' }));

  /**
   * Every route this server answers, which is the only documentation that
   * cannot go stale. Read off the router rather than kept in a list, because a
   * list beside the routes is a list that stops matching them.
   */
  app.get('/routes', () => ({
    routes: routeEntries(app).map((entry) => ({
      path: entry.path,
      methods: [...entry.methods],
    })),
  }));

  app.get('/job', () => {
    const job = service.job;
    return {
      id: job.settings.id,
      name: job.settings.name,
      currency: job.settings.currency,
      contractSum: moneyDto(job.settings.contractSum),
      counts: {
        alignments: job.alignments.count(),
        billRevisions: job.billRevisions.count(),
        packages: job.packages.count(),
        plans: job.plans.count(),
        signOffs: job.signOffs.count(),
        ncrs: job.ncrs.count(),
        records: job.records.count(),
        materials: job.materials.count(),
        variations: job.variations.count(),
        dayworkSheets: job.dayworkSheets.count(),
        delayEvents: job.delayEvents.count(),
        defects: job.defects.count(),
        subcontracts: job.subcontracts.count(),
        paymentApplications: job.paymentApplications.count(),
        resourceReturns: job.resourceReturns.count(),
        indices: job.indices.count(),
        haulSites: job.haulSites.count(),
        earlyWarnings: job.earlyWarnings.count(),
      },
      blockers: jobBlockers(job),
    };
  });

  app.get('/schedule', (request) => {
    const query = request.query as Record<string, unknown>;
    return scheduleOf(service, countQuery(query['periods'], 12, 'periods')).periods.map(
      periodDto,
    );
  });

  app.get('/bill', (request) => {
    const query = request.query as Record<string, unknown>;
    const context = contextOf(service);
    const asAt = dateQuery(query['asAt'], scheduleOf(service, 1).periods[0]!.cutOff);
    const bill = billAsAt(context.billHistory, asAt);
    return {
      id: bill.id,
      title: bill.title,
      currency: bill.currency,
      items: bill.items.map((item) => ({
        code: formatItemCode(item.code),
        description: item.description,
        unit: item.unit,
        rate: rateMajor(item.rate),
        quantity: formatQuantity(item.quantity),
        kind: item.kind,
      })),
    };
  });

  app.get('/packages', () =>
    service.job.packages.all().map((pkg) => ({
      id: pkg.id,
      name: pkg.name,
      section: pkg.section,
      alignmentId: pkg.alignmentId,
      extent: formatChainageRange(pkg.extent),
      profileId: pkg.profileId,
      allocations: pkg.allocations.map((a) => ({
        code: formatItemCode(a.code),
        quantity: formatQuantity(a.quantity),
      })),
    })),
  );

  app.get('/packages/:id', (request) => {
    const params = request.params as { id: string };
    const pkg = service.job.packages.require(params.id);
    return {
      id: pkg.id,
      name: pkg.name,
      section: pkg.section,
      extent: formatChainageRange(pkg.extent),
      profileId: pkg.profileId,
      allocations: pkg.allocations.map((a) => ({
        code: formatItemCode(a.code),
        quantity: formatQuantity(a.quantity),
      })),
    };
  });

  app.get('/records', (request) => {
    const query = request.query as Record<string, unknown>;
    const asAt = dateQuery(query['asAt'], scheduleOf(service, 999).periods.at(-1)!.cutOff);
    const packageId = query['package'];
    return effectiveRecords(service.job.records.all(), asAt)
      .filter((r) => packageId === undefined || r.packageId === packageId)
      .map((record) => ({
        id: record.id,
        packageId: record.packageId,
        code: formatItemCode(record.code),
        stepId: record.stepId,
        extent: formatChainageRange(record.extent),
        quantity: formatQuantity(record.quantity),
        measuredOn: formatSiteDate(record.measuredOn),
        bookedBy: record.bookedBy,
        method: record.method,
      }));
  });

  /**
   * Every report on a valuation hangs off the same path and answers the same
   * two ways. A report the contract has no term for comes back 204 rather than
   * 404: there is nothing wrong with the request and nothing wrong with the
   * job, so a 404 would send a client looking for a routing mistake that is
   * not there.
   */
  function valuationRoute<T>(
    suffix: string,
    build: (service: Service, valuation: number) => T | null,
    dto: (report: T) => Record<string, unknown>,
  ): void {
    app.get(`/valuations/:number/${suffix}`, (request, reply) => {
      const params = request.params as { number: string };
      const report = build(service, numberParam(params.number, 'valuation'));
      if (report === null) {
        void reply.status(204).send();
        return reply;
      }
      return dto(report);
    });
  }

  valuationRoute('progress', progressFor, progressReportDto);
  valuationRoute('certificate', certificateFor, certificateDto);
  valuationRoute('delay', delayFor, delayReportDto);
  valuationRoute('defects', defectsFor, defectsReportDto);
  valuationRoute('payments', paymentsFor, subcontractReportDto);
  valuationRoute('output', outputFor, outputReportDto);
  valuationRoute('outturn', outturnFor, outturnDto);
  valuationRoute('variations', variationsFor, variationReportDto);
  valuationRoute('risks', risksFor, riskPositionDto);

  app.get('/valuations/:number/strip/:packageId', (request) => {
    const params = request.params as { number: string; packageId: string };
    return stripDto(
      stripFor(service, params.packageId, numberParam(params.number, 'valuation')),
    );
  });

  /**
   * The late query is a what if, not a record of anything. It is on the report
   * rather than on the job because the question is always asked twice, once
   * against the terms and once against what the payer actually does.
   */
  /**
   * The lookahead is the one report keyed on a date rather than a valuation,
   * and it defaults to the first cut-off rather than to today. Nothing this
   * server answers means "now", so that two people running the same query on
   * different days get the same answer.
   */
  app.get('/lookahead', (request) => {
    const query = request.query as Record<string, unknown>;
    const from = dateQuery(query['from'], scheduleOf(service, 1).periods[0]!.cutOff);
    return lookaheadDto(
      lookaheadFrom(service, from, {
        weeks: countQuery(query['weeks'], 4, 'weeks'),
        gangs: countQuery(query['gangs'], 1, 'gangs'),
      }),
    );
  });

  app.get('/haul', () => haulPlanDto(haulPlan(service)));

  app.get('/revisions', () => revisionReportDto(revisionsFor(service)));

  /**
   * The health check answers 200 with the findings rather than 500 on a job
   * with problems. A monitor wants to know the server is up; a person wants to
   * know the job is sound, and conflating the two makes the endpoint useless
   * for both.
   */
  app.get('/doctor', () => {
    const diagnosis = diagnose(service.job);
    return {
      healthy: isHealthy(diagnosis),
      errors: diagnosis.errors,
      warnings: diagnosis.warnings,
      blockers: [...diagnosis.blockers],
      findings: diagnosis.findings.map((item) => ({
        level: item.level,
        register: item.register,
        subject: item.subject,
        message: item.message,
      })),
    };
  });

  app.get('/cash', (request) => {
    const query = request.query as Record<string, unknown>;
    return cashFlowDto(
      cashFlowFor(
        service,
        countQuery(query['periods'], 12, 'periods'),
        fromZeroQuery(query['late'], 'late'),
      ),
    );
  });

  app.get('/curve', (request) => {
    const query = request.query as Record<string, unknown>;
    return curveDto(curveFor(service, countQuery(query['periods'], 12, 'periods')));
  });

  /**
   * An import answers 207 when it took some rows and left others out. A flat
   * 200 would tell a client that uploaded four hundred rows and had two
   * rejected that everything went in.
   */
  function importRoute<T>(
    path: string,
    load: (service: Service, text: string, options: { partial: boolean }) => ImportReport<T>,
  ): void {
    app.post(path, (request, reply) => {
      const query = request.query as Record<string, unknown>;
      const partial = query['partial'] === 'true';
      const body = typeof request.body === 'string' ? request.body : '';
      const dto = importReportDto(load(service, body, { partial }));
      void reply.status(dto['rejected'] === 0 ? 200 : 207).send(dto);
    });
  }

  importRoute('/import/records', loadRecords);
  importRoute('/import/sign-offs', loadSignOffs);
  importRoute('/import/ncrs', loadNcrs);
  importRoute('/import/diary', loadDiary);
  importRoute('/import/applications', loadApplications);
  importRoute('/import/notices', loadNotices);
  importRoute('/import/returns', loadResourceReturns);
  importRoute('/import/indices', loadIndices);
  importRoute('/import/pay-less', loadPayLessNotices);

  return app;
}

export interface RouteEntry {
  readonly path: string;
  readonly methods: readonly string[];
}

/**
 * Every route the server answers, read off the printed route tree.
 *
 * Fastify has no public lookup for its routes, so the tree it prints is
 * parsed. That is ugly, and the alternative is a table of paths kept beside
 * the routes by hand, which stops matching them the first week.
 *
 * The tree nests shared prefixes, so the parse has to carry a stack rather
 * than read each line on its own. Reading the lines flat makes the child of
 * `/packages` look like a top level `/:id`, which then matches every one
 * segment path there is.
 */
export function routeEntries(app: FastifyInstance): RouteEntry[] {
  const entries: RouteEntry[] = [];
  const stack: string[] = [];

  for (const line of app.printRoutes({ commonPrefix: false }).split('\n')) {
    const match =
      /^((?:(?:\u2502 {3}| {4})*))(?:\u251c\u2500\u2500|\u2514\u2500\u2500)? ?(\/\S*)\s+\(([^)]+)\)/.exec(
        line,
      );
    if (match === null) continue;
    const depth = Math.floor((match[1] ?? '').length / 4);
    stack.length = depth;
    stack.push(match[2] ?? '');
    entries.push({
      path: stack.join(''),
      methods: (match[3] ?? '').split(',').map((method) => method.trim()),
    });
  }
  return entries;
}

/** The methods a path would answer, so a wrong method can say what to use. */
function methodsFor(app: FastifyInstance, url: string): string[] {
  const path = url.split('?')[0] ?? url;
  const segments = path.split('/').filter((part) => part.length > 0);
  const methods = new Set<string>();

  for (const entry of routeEntries(app)) {
    const route = entry.path.split('/').filter((part) => part.length > 0);
    if (route.length !== segments.length) continue;
    const fits = route.every((part, index) => part.startsWith(':') || part === segments[index]);
    if (fits) for (const method of entry.methods) methods.add(method);
  }
  return [...methods].sort();
}
