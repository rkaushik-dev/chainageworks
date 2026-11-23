package io.github.rkaushikdev.chainageworks.app;

import io.github.rkaushikdev.chainageworks.domain.bill.BillItem;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.credit.CreditStep;
import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkSchedule;
import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkSheet;
import io.github.rkaushikdev.chainageworks.domain.daywork.LabourEntry;
import io.github.rkaushikdev.chainageworks.domain.daywork.PlantEntry;
import io.github.rkaushikdev.chainageworks.domain.earthworks.HaulLink;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.AdjustmentTerms;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.Weighting;
import io.github.rkaushikdev.chainageworks.domain.itp.InspectionPoint;
import io.github.rkaushikdev.chainageworks.domain.itp.Ncr;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOff;
import io.github.rkaushikdev.chainageworks.domain.packages.PackageAllocation;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceReturn;
import io.github.rkaushikdev.chainageworks.domain.risk.EarlyWarning;
import io.github.rkaushikdev.chainageworks.domain.subcontract.ContraCharge;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentApplication;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentNotice;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecord;
import io.github.rkaushikdev.chainageworks.domain.survey.ToleranceSet;
import io.github.rkaushikdev.chainageworks.progress.OutputAllowance;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.valuation.MaterialsOnSite;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The check a job is given before anybody trusts a number off it.
 *
 * <p>Nearly every check here is for a dangling reference rather than for a bad value, because the
 * builders already refuse bad values and a reference can only be checked once everything is loaded.
 * A sign off against a point no plan carries is the case that keeps happening: it imports cleanly,
 * it gates nothing, and the work it should have released stays held with no complaint anywhere until
 * somebody asks why the valuation is short. A step is checked against the package's own rule of
 * credit, not against every rule on the job, because a record booked to a step another rule has
 * still earns nothing. The few warnings that are not references are the records the certificate
 * quietly leaves out: an unbonded lot off site, a happened risk nobody has quoted.
 */
public final class Doctor {

    private Doctor() {}

    /** Walk the whole job looking for references that go nowhere. */
    public static Diagnosis diagnose(Job job) {
        Set<String> packageIds = Set.copyOf(job.packages().ids());
        Set<String> profileIds = Set.copyOf(job.profiles().ids());
        Set<String> alignmentIds = Set.copyOf(job.alignments().ids());
        Set<String> subcontractIds = Set.copyOf(job.subcontracts().ids());
        Set<String> applicationIds = Set.copyOf(job.paymentApplications().ids());
        Set<String> siteIds = Set.copyOf(job.haulSites().ids());
        Set<String> indexIds = Set.copyOf(job.indices().ids());
        Set<String> pointIds = job.plans().all().stream()
                .flatMap(plan -> plan.points().stream())
                .map(InspectionPoint::id)
                .collect(Collectors.toSet());
        Set<String> stepIds = job.profiles().all().stream()
                .flatMap(profile -> profile.steps().stream())
                .map(CreditStep::id)
                .collect(Collectors.toSet());
        Set<String> billed = job.billRevisions().all().stream()
                .flatMap(revision -> revision.bill().items().stream())
                .map(item -> item.code().key())
                .collect(Collectors.toSet());

        List<Finding> findings = new ArrayList<>();

        for (WorkPackage pkg : job.packages().all()) {
            if (!alignmentIds.contains(pkg.alignmentId())) {
                findings.add(Finding.error("packages", pkg.id(), "no alignment " + pkg.alignmentId() + " is loaded"));
            }
            if (!profileIds.contains(pkg.profileId())) {
                findings.add(
                        Finding.error("packages", pkg.id(), "no rule of credit " + pkg.profileId() + " is loaded"));
            }
            for (PackageAllocation allocation : pkg.allocations()) {
                if (!billed.contains(allocation.code().key())) {
                    findings.add(Finding.error(
                            "packages", pkg.id(), allocation.code().format() + " is not in any bill revision"));
                }
            }
            if (job.records().where(r -> r.packageId().equals(pkg.id())).isEmpty()) {
                findings.add(Finding.warning("packages", pkg.id(), "nothing has been measured yet"));
            }
        }

        for (SignOff signOff : job.signOffs().all()) {
            if (!pointIds.contains(signOff.pointId())) {
                findings.add(Finding.error(
                        "sign offs",
                        signOff.id(),
                        "no inspection plan carries the point " + signOff.pointId() + ", so it gates nothing"));
            }
            if (!packageIds.contains(signOff.packageId())) {
                findings.add(
                        Finding.error("sign offs", signOff.id(), "no package " + signOff.packageId() + " is loaded"));
            }
        }

        for (Ncr ncr : job.ncrs().all()) {
            if (!packageIds.contains(ncr.packageId())) {
                findings.add(Finding.error("reports", ncr.id(), "no package " + ncr.packageId() + " is loaded"));
            }
            Optional<String> step = ncr.stepId();
            if (step.isPresent() && lacksStep(job, ncr.packageId(), step.get())) {
                findings.add(
                        Finding.warning("reports", ncr.id(), "the package's rule of credit has no step " + step.get()));
            }
        }

        for (SurveyRecord record : job.records().all()) {
            if (!packageIds.contains(record.packageId())) {
                findings.add(Finding.error("records", record.id(), "no package " + record.packageId() + " is loaded"));
            } else if (lacksStep(job, record.packageId(), record.stepId())) {
                findings.add(Finding.error(
                        "records", record.id(), "the package's rule of credit has no step " + record.stepId()));
            }
        }

        for (PaymentApplication application : job.paymentApplications().all()) {
            if (!subcontractIds.contains(application.subcontractId())) {
                findings.add(Finding.error(
                        "applications",
                        application.id(),
                        "no subcontract " + application.subcontractId() + " is loaded"));
            }
        }

        for (PaymentNotice notice : job.paymentNotices().all()) {
            if (!applicationIds.contains(notice.applicationId())) {
                findings.add(Finding.error(
                        "notices", notice.applicationId(), "the notice answers an application that is not loaded"));
            }
        }

        for (ContraCharge charge : job.contraCharges().all()) {
            if (!subcontractIds.contains(charge.subcontractId())) {
                findings.add(Finding.error(
                        "contra charges", charge.id(), "no subcontract " + charge.subcontractId() + " is loaded"));
            }
        }

        for (ResourceReturn entry : job.resourceReturns().all()) {
            Optional<String> step = entry.stepId();
            if (!packageIds.contains(entry.packageId())) {
                findings.add(Finding.error("returns", entry.id(), "no package " + entry.packageId() + " is loaded"));
            } else if (step.isPresent() && lacksStep(job, entry.packageId(), step.get())) {
                findings.add(Finding.warning(
                        "returns",
                        entry.id(),
                        "the hours are booked to " + step.get()
                                + ", which the package's rule of credit does not have"));
            }
        }

        findings.addAll(materialsFindings(job, packageIds, billed));
        findings.addAll(dayworkFindings(job, packageIds));
        findings.addAll(riskFindings(job));
        findings.addAll(settingsFindings(job, siteIds, stepIds, indexIds));

        return Diagnosis.of(findings, job.blockers());
    }

    /**
     * True when the rule of credit a package earns by has no such step. A package the job does not
     * carry, or one whose rule is missing, is reported on its own, so nothing is said twice.
     */
    private static boolean lacksStep(Job job, String packageId, String stepId) {
        return job.packages()
                .find(packageId)
                .flatMap(pkg -> job.profiles().find(pkg.profileId()))
                .map(profile -> profile.findStep(stepId).isEmpty())
                .orElse(false);
    }

    /**
     * Lots booked against nothing, and lots held off site with nothing securing them. The second is
     * a warning rather than an error: the certificate still builds, it just leaves the lot off.
     */
    private static List<Finding> materialsFindings(Job job, Set<String> packageIds, Set<String> billed) {
        List<Finding> findings = new ArrayList<>();
        for (MaterialsOnSite lot : job.materials().all()) {
            if (!packageIds.contains(lot.packageId())) {
                findings.add(Finding.error("materials", lot.id(), "no package " + lot.packageId() + " is loaded"));
            }
            if (!billed.contains(lot.code().key())) {
                findings.add(
                        Finding.error("materials", lot.id(), lot.code().format() + " is not in any bill revision"));
            }
            if (lot.isOffSite() && lot.bondReference().isEmpty()) {
                findings.add(Finding.warning(
                        "materials",
                        lot.id(),
                        "held at " + lot.storedAt().orElseThrow() + " with no bond, so it cannot be certified"));
            }
        }
        return findings;
    }

    /**
     * Sheets that name a rate the schedule does not carry. That one is an error and not a warning:
     * the sheet cannot be priced, and pricing it is part of building every certificate.
     */
    private static List<Finding> dayworkFindings(Job job, Set<String> packageIds) {
        List<Finding> findings = new ArrayList<>();
        Optional<DayworkSchedule> schedule = job.settings().daywork();
        for (DayworkSheet sheet : job.dayworkSheets().all()) {
            if (!packageIds.contains(sheet.packageId())) {
                findings.add(Finding.warning("daywork", sheet.id(), "no package " + sheet.packageId() + " is loaded"));
            }
            if (schedule.isEmpty()) {
                findings.add(Finding.warning(
                        "daywork", sheet.id(), "the contract has no daywork schedule, so the sheet is never valued"));
                continue;
            }
            sheet.labour().stream()
                    .map(LabourEntry::rateId)
                    .filter(rate -> schedule.get().findLabourRate(rate).isEmpty())
                    .forEach(rate -> findings.add(
                            Finding.error("daywork", sheet.id(), "the schedule has no labour rate " + rate)));
            sheet.plant().stream()
                    .map(PlantEntry::rateId)
                    .filter(rate -> schedule.get().findPlantRate(rate).isEmpty())
                    .forEach(rate -> findings.add(
                            Finding.error("daywork", sheet.id(), "the schedule has no plant rate " + rate)));
        }
        return findings;
    }

    /** Risks that have happened and are still carried at the estimate, waiting on a quotation. */
    private static List<Finding> riskFindings(Job job) {
        return job.earlyWarnings().all().stream()
                .filter(EarlyWarning::awaitsQuotation)
                .map(warning -> Finding.warning(
                        "risks",
                        warning.id(),
                        "it has happened and is still carried at the estimate until it is quoted"))
                .toList();
    }

    /** Contract terms that name sites, steps, indices or bill headings the job does not carry. */
    private static List<Finding> settingsFindings(
            Job job, Set<String> siteIds, Set<String> stepIds, Set<String> indexIds) {
        List<Finding> findings = new ArrayList<>();
        for (HaulLink link : job.settings().haulLinks()) {
            for (String id : List.of(link.fromId(), link.toId())) {
                if (!siteIds.contains(id)) {
                    findings.add(Finding.warning("haul", link.fromId() + " to " + link.toId(), "no haul site " + id));
                }
            }
        }

        for (OutputAllowance allowance : job.settings().outputAllowances()) {
            if (!stepIds.contains(allowance.stepId())) {
                findings.add(Finding.warning(
                        "allowances",
                        allowance.stepId(),
                        "no rule of credit has that step, so the allowance is never used"));
            }
        }

        List<ItemCode> codes = job.billRevisions().all().stream()
                .flatMap(revision -> revision.bill().items().stream())
                .map(BillItem::code)
                .toList();
        for (ToleranceSet.Rule rule : job.settings().tolerances().rules()) {
            if (rule.specificity() > 1 && !codes.isEmpty() && codes.stream().noneMatch(rule::covers)) {
                findings.add(Finding.warning(
                        "tolerances",
                        rule.heading().get().format(),
                        "no bill item falls under the heading, so the allowance is never used"));
            }
        }

        Optional<AdjustmentTerms> clause = job.settings().adjustment();
        if (clause.isPresent()) {
            for (Weighting weighting : clause.get().weightings()) {
                if (!indexIds.contains(weighting.indexId())) {
                    findings.add(Finding.error(
                            "fluctuations",
                            weighting.indexId(),
                            "the clause weights an index nobody has loaded, so no certificate can be built"));
                }
            }
        }
        return findings;
    }
}
