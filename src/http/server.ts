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
  loadNcrs,
  loadNotices,
  loadPayLessNotices,
  outputFor,
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
  moneyDto,
  outputReportDto,
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
  const app = Fastify({ logger: false });

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

  app.setNotFoundHandler((_request, reply) => {
    void reply.status(404).send({
      code: 'not_found',
      message: 'no such route',
      details: {},
    });
  });

  app.get('/health', () => ({ status: 'ok' }));

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
  importRoute('/import/pay-less', loadPayLessNotices);

  return app;
}
