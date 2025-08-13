import { formatItemCode, itemCodeKey } from '../domain/bill/itemCode.js';
import type { Job } from '../store/job.js';
import { jobBlockers } from '../store/job.js';

/**
 * How badly a finding breaks the job.
 *
 * An error means a number somewhere is wrong or a report will refuse to build.
 * A warning means something is odd and worth a look but the valuation still
 * stands. The distinction is what makes the list worth reading: a check that
 * reports everything at one level gets ignored the first week.
 */
export type FindingLevel = 'error' | 'warning';

export interface Finding {
  readonly level: FindingLevel;
  /** The register the problem is in, so it can be sorted by whose job it is. */
  readonly register: string;
  readonly subject: string;
  readonly message: string;
}

export interface Diagnosis {
  readonly findings: readonly Finding[];
  readonly errors: number;
  readonly warnings: number;
  readonly blockers: readonly string[];
}

function finding(
  level: FindingLevel,
  register: string,
  subject: string,
  message: string,
): Finding {
  return { level, register, subject, message };
}

/**
 * Walk the whole job looking for references that go nowhere.
 *
 * Every check here is for a dangling reference rather than for a bad value,
 * because the builders already refuse bad values and a reference can only be
 * checked once everything is loaded. A sign-off against a point no plan
 * carries is the case that keeps happening: it imports cleanly, it gates
 * nothing, and the work it should have released stays held with no complaint
 * anywhere until somebody asks why the valuation is short.
 */
export function diagnose(job: Job): Diagnosis {
  const findings: Finding[] = [];

  const packageIds = new Set(job.packages.all().map((p) => p.id));
  const profileIds = new Set(job.profiles.all().map((p) => p.id));
  const alignmentIds = new Set(job.alignments.all().map((a) => a.id));
  const pointIds = new Set(job.plans.all().flatMap((plan) => plan.points.map((p) => p.id)));
  const stepIds = new Set(job.profiles.all().flatMap((p) => p.steps.map((s) => s.id)));
  const subcontractIds = new Set(job.subcontracts.all().map((s) => s.id));
  const applicationIds = new Set(job.paymentApplications.all().map((a) => a.id));
  const siteIds = new Set(job.haulSites.all().map((s) => s.id));
  const indexIds = new Set(job.indices.all().map((i) => i.id));

  const billed = new Set(
    job.billRevisions
      .all()
      .flatMap((revision) => revision.bill.items.map((item) => itemCodeKey(item.code))),
  );

  for (const pkg of job.packages.all()) {
    if (!alignmentIds.has(pkg.alignmentId)) {
      findings.push(
        finding('error', 'packages', pkg.id, `no alignment ${pkg.alignmentId} is loaded`),
      );
    }
    if (!profileIds.has(pkg.profileId)) {
      findings.push(
        finding('error', 'packages', pkg.id, `no rule of credit ${pkg.profileId} is loaded`),
      );
    }
    for (const allocation of pkg.allocations) {
      if (!billed.has(itemCodeKey(allocation.code))) {
        findings.push(
          finding(
            'error',
            'packages',
            pkg.id,
            `${formatItemCode(allocation.code)} is not in any bill revision`,
          ),
        );
      }
    }
    if (job.records.where((r) => r.packageId === pkg.id).length === 0) {
      findings.push(finding('warning', 'packages', pkg.id, 'nothing has been measured yet'));
    }
  }

  for (const signOff of job.signOffs.all()) {
    if (!pointIds.has(signOff.pointId)) {
      findings.push(
        finding(
          'error',
          'sign offs',
          signOff.id,
          `no inspection plan carries the point ${signOff.pointId}, so it gates nothing`,
        ),
      );
    }
    if (!packageIds.has(signOff.packageId)) {
      findings.push(
        finding('error', 'sign offs', signOff.id, `no package ${signOff.packageId} is loaded`),
      );
    }
  }

  for (const ncr of job.ncrs.all()) {
    if (!packageIds.has(ncr.packageId)) {
      findings.push(
        finding('error', 'reports', ncr.id, `no package ${ncr.packageId} is loaded`),
      );
    }
    if (ncr.stepId !== null && !stepIds.has(ncr.stepId)) {
      findings.push(
        finding('warning', 'reports', ncr.id, `no rule of credit has a step ${ncr.stepId}`),
      );
    }
  }

  for (const record of job.records.all()) {
    if (!packageIds.has(record.packageId)) {
      findings.push(
        finding('error', 'records', record.id, `no package ${record.packageId} is loaded`),
      );
    } else if (!stepIds.has(record.stepId)) {
      findings.push(
        finding('error', 'records', record.id, `no rule of credit has a step ${record.stepId}`),
      );
    }
  }

  for (const application of job.paymentApplications.all()) {
    if (!subcontractIds.has(application.subcontractId)) {
      findings.push(
        finding(
          'error',
          'applications',
          application.id,
          `no subcontract ${application.subcontractId} is loaded`,
        ),
      );
    }
  }

  for (const notice of job.paymentNotices.all()) {
    if (!applicationIds.has(notice.applicationId)) {
      findings.push(
        finding(
          'error',
          'notices',
          notice.applicationId,
          'the notice answers an application that is not loaded',
        ),
      );
    }
  }

  for (const charge of job.contraCharges.all()) {
    if (!subcontractIds.has(charge.subcontractId)) {
      findings.push(
        finding(
          'error',
          'contra charges',
          charge.id,
          `no subcontract ${charge.subcontractId} is loaded`,
        ),
      );
    }
  }

  for (const entry of job.resourceReturns.all()) {
    if (!packageIds.has(entry.packageId)) {
      findings.push(
        finding('error', 'returns', entry.id, `no package ${entry.packageId} is loaded`),
      );
    } else if (entry.stepId !== null && !stepIds.has(entry.stepId)) {
      findings.push(
        finding(
          'warning',
          'returns',
          entry.id,
          `the hours are booked to ${entry.stepId}, which no rule of credit has`,
        ),
      );
    }
  }

  for (const link of job.settings.haulLinks) {
    for (const id of [link.fromId, link.toId]) {
      if (!siteIds.has(id)) {
        findings.push(
          finding('warning', 'haul', `${link.fromId} to ${link.toId}`, `no haul site ${id}`),
        );
      }
    }
  }

  for (const allowance of job.settings.outputAllowances) {
    if (!stepIds.has(allowance.stepId)) {
      findings.push(
        finding(
          'warning',
          'allowances',
          allowance.stepId,
          'no rule of credit has that step, so the allowance is never used',
        ),
      );
    }
  }

  const clause = job.settings.adjustment;
  if (clause !== null) {
    for (const weighting of clause.weightings) {
      if (!indexIds.has(weighting.indexId)) {
        findings.push(
          finding(
            'error',
            'fluctuations',
            weighting.indexId,
            'the clause weights an index nobody has loaded, so no certificate can be built',
          ),
        );
      }
    }
  }

  return {
    findings,
    errors: findings.filter((f) => f.level === 'error').length,
    warnings: findings.filter((f) => f.level === 'warning').length,
    blockers: jobBlockers(job),
  };
}

export function isHealthy(diagnosis: Diagnosis): boolean {
  return diagnosis.errors === 0 && diagnosis.blockers.length === 0;
}

/** Findings from one register, which is how the list gets handed out. */
export function findingsIn(diagnosis: Diagnosis, register: string): Finding[] {
  return diagnosis.findings.filter((f) => f.register === register);
}

export function describeFinding(item: Finding): string {
  return `${item.level} ${item.register} ${item.subject}: ${item.message}`;
}
