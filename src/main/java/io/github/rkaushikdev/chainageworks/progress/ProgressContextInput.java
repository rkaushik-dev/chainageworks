package io.github.rkaushikdev.chainageworks.progress;

import io.github.rkaushikdev.chainageworks.domain.bill.BillHistory;
import io.github.rkaushikdev.chainageworks.domain.credit.ProfileRegistry;
import io.github.rkaushikdev.chainageworks.domain.itp.InspectionPlan;
import io.github.rkaushikdev.chainageworks.domain.itp.Ncr;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOff;
import io.github.rkaushikdev.chainageworks.domain.packages.PackageRegister;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecord;
import io.github.rkaushikdev.chainageworks.domain.survey.ToleranceSet;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import java.util.List;

/**
 * What a progress context is built from. {@code null} lists mean none and {@code null} tolerances
 * mean the defaults.
 *
 * @param billHistory every revision of the bill
 * @param register the work packages
 * @param profiles the rules of credit
 * @param plans the inspection plans, at most one per rule of credit
 * @param signOffs the inspections carried out
 * @param ncrs the non conformance reports
 * @param records the survey book
 * @param calendar the site calendar
 * @param tolerances how far a measurement may run past its allocation
 */
public record ProgressContextInput(
        BillHistory billHistory,
        PackageRegister register,
        ProfileRegistry profiles,
        List<InspectionPlan> plans,
        List<SignOff> signOffs,
        List<Ncr> ncrs,
        List<SurveyRecord> records,
        SiteCalendar calendar,
        ToleranceSet tolerances) {

    public ProgressContextInput {
        plans = plans == null ? List.of() : List.copyOf(plans);
        signOffs = signOffs == null ? List.of() : List.copyOf(signOffs);
        ncrs = ncrs == null ? List.of() : List.copyOf(ncrs);
        records = records == null ? List.of() : List.copyOf(records);
    }

    /** A context with no plans, no inspections, no reports and no survey records. */
    public static ProgressContextInput of(
            BillHistory billHistory, PackageRegister register, ProfileRegistry profiles, SiteCalendar calendar) {
        return new ProgressContextInput(billHistory, register, profiles, null, null, null, null, calendar, null);
    }

    public ProgressContextInput withPlans(List<InspectionPlan> other) {
        return new ProgressContextInput(
                billHistory, register, profiles, other, signOffs, ncrs, records, calendar, tolerances);
    }

    public ProgressContextInput withSignOffs(List<SignOff> other) {
        return new ProgressContextInput(
                billHistory, register, profiles, plans, other, ncrs, records, calendar, tolerances);
    }

    public ProgressContextInput withNcrs(List<Ncr> other) {
        return new ProgressContextInput(
                billHistory, register, profiles, plans, signOffs, other, records, calendar, tolerances);
    }

    public ProgressContextInput withRecords(List<SurveyRecord> other) {
        return new ProgressContextInput(
                billHistory, register, profiles, plans, signOffs, ncrs, other, calendar, tolerances);
    }

    public ProgressContextInput withTolerances(ToleranceSet other) {
        return new ProgressContextInput(
                billHistory, register, profiles, plans, signOffs, ncrs, records, calendar, other);
    }
}
