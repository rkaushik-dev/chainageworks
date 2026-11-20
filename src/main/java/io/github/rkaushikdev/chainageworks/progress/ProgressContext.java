package io.github.rkaushikdev.chainageworks.progress;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.bill.BillHistory;
import io.github.rkaushikdev.chainageworks.domain.credit.CreditProfile;
import io.github.rkaushikdev.chainageworks.domain.credit.ProfileRegistry;
import io.github.rkaushikdev.chainageworks.domain.itp.InspectionPlan;
import io.github.rkaushikdev.chainageworks.domain.itp.Ncr;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOff;
import io.github.rkaushikdev.chainageworks.domain.packages.PackageRegister;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecord;
import io.github.rkaushikdev.chainageworks.domain.survey.ToleranceSet;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything the progress engine reads.
 *
 * <p>Gathered into one value on purpose. The calculation is a composition of six separate
 * registers and passing them one at a time down four call levels made it far too easy to answer a
 * question against last month's sign-offs and this month's bill.
 *
 * @param billHistory every revision of the bill
 * @param register the work packages
 * @param profiles the rules of credit
 * @param plans inspection plans keyed by the rule of credit they gate
 * @param signOffs the inspections carried out
 * @param ncrs the non conformance reports
 * @param records the survey book
 * @param calendar the site calendar
 * @param tolerances how far a measurement may run past its allocation
 */
public record ProgressContext(
        BillHistory billHistory,
        PackageRegister register,
        ProfileRegistry profiles,
        Map<String, InspectionPlan> plans,
        List<SignOff> signOffs,
        List<Ncr> ncrs,
        List<SurveyRecord> records,
        SiteCalendar calendar,
        ToleranceSet tolerances) {

    public ProgressContext {
        plans = Collections.unmodifiableMap(new LinkedHashMap<>(plans));
        signOffs = List.copyOf(signOffs);
        ncrs = List.copyOf(ncrs);
        records = List.copyOf(records);
    }

    /** Build a context, refusing two inspection plans for one rule of credit. */
    public static ProgressContext build(ProgressContextInput input) {
        Map<String, InspectionPlan> plans = new LinkedHashMap<>();
        for (InspectionPlan plan : input.plans()) {
            if (plans.containsKey(plan.profileId())) {
                throw new BadInputException(
                        "two inspection plans gate the same rule of credit", Details.of("profile", plan.profileId()));
            }
            plans.put(plan.profileId(), plan);
        }
        return new ProgressContext(
                input.billHistory(),
                input.register(),
                input.profiles(),
                plans,
                input.signOffs(),
                input.ncrs(),
                input.records(),
                input.calendar(),
                input.tolerances() == null ? ToleranceSet.DEFAULT : input.tolerances());
    }

    public CreditProfile profileFor(WorkPackage pkg) {
        return profiles.require(pkg.profileId());
    }

    /** The plan gating a package's rule of credit. A package with no plan is simply ungated. */
    public Optional<InspectionPlan> planFor(WorkPackage pkg) {
        return Optional.ofNullable(plans.get(pkg.profileId()));
    }

    public List<SignOff> signOffsFor(String packageId) {
        return signOffs.stream().filter(s -> s.packageId().equals(packageId)).toList();
    }

    public List<Ncr> ncrsFor(String packageId) {
        return ncrs.stream().filter(n -> n.packageId().equals(packageId)).toList();
    }

    public List<SurveyRecord> recordsFor(String packageId) {
        return records.stream().filter(r -> r.packageId().equals(packageId)).toList();
    }

    public ProgressContext withRegister(PackageRegister other) {
        return new ProgressContext(billHistory, other, profiles, plans, signOffs, ncrs, records, calendar, tolerances);
    }

    public ProgressContext withRecords(List<SurveyRecord> other) {
        return new ProgressContext(billHistory, register, profiles, plans, signOffs, ncrs, other, calendar, tolerances);
    }

    public ProgressContext withBillHistory(BillHistory other) {
        return new ProgressContext(other, register, profiles, plans, signOffs, ncrs, records, calendar, tolerances);
    }

    public ProgressContext withCalendar(SiteCalendar other) {
        return new ProgressContext(billHistory, register, profiles, plans, signOffs, ncrs, records, other, tolerances);
    }
}
