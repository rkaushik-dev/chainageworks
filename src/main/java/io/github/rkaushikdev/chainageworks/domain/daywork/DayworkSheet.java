package io.github.rkaushikdev.chainageworks.domain.daywork;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A daywork sheet as it comes off site.
 *
 * <p>The signature is the whole point. An unsigned sheet is a record of what the contractor says
 * happened, and it is worth nothing on a certificate: the engineer has to have seen the gang on the
 * day, because nobody can verify three weeks later that four men were there for six hours.
 *
 * @param id the sheet's key
 * @param reference the sheet number on the pad
 * @param packageId the package the work was in
 * @param instruction the instruction this work was done under
 * @param workedOn the day the work was done
 * @param signedOn the day the engineer signed it, absent while unsigned
 * @param signedBy who signed it, empty while unsigned
 * @param labour the gangs
 * @param plant the plant
 * @param materials the materials
 */
public record DayworkSheet(
        String id,
        String reference,
        String packageId,
        String instruction,
        LocalDate workedOn,
        Optional<LocalDate> signedOn,
        String signedBy,
        List<LabourEntry> labour,
        List<PlantEntry> plant,
        List<MaterialEntry> materials) {

    public DayworkSheet {
        signedOn = signedOn == null ? Optional.empty() : signedOn;
        labour = List.copyOf(labour);
        plant = List.copyOf(plant);
        materials = List.copyOf(materials);
    }

    /**
     * What one sheet is worth, head by head.
     *
     * @param labour labour with its addition
     * @param plant plant with its addition
     * @param materials materials with their addition
     * @param total the three together
     */
    public record Value(Money labour, Money plant, Money materials, Money total) {}

    private static void checkHours(double hours, String what) {
        if (!Double.isFinite(hours) || hours < 0) {
            throw new BadInputException(what + " cannot be negative", Details.of("hours", hours));
        }
        if (hours > 24) {
            throw new BadInputException(what + " cannot be more than a day", Details.of("hours", hours));
        }
    }

    /**
     * Build a sheet, refusing a signature with nobody named, a signature before the work was done,
     * a labour line with nobody on it, hours that are negative or longer than a day, and a sheet
     * with nothing on it.
     */
    public static DayworkSheet build(DayworkSheetInput input) {
        Optional<LocalDate> signedOn = Optional.ofNullable(input.signedOn());
        String signedBy = Text.trim(input.signedBy() == null ? "" : input.signedBy());
        if (signedOn.isPresent() && signedBy.isEmpty()) {
            throw new BadInputException(
                    "a signed daywork sheet has to name who signed it", Details.of("sheet", input.id()));
        }
        if (signedOn.isPresent() && signedOn.get().isBefore(input.workedOn())) {
            throw new BadInputException(
                    "a daywork sheet cannot be signed before the work was done",
                    Details.of(
                            "sheet", input.id(),
                            "worked", SiteDates.format(input.workedOn()),
                            "signed", SiteDates.format(signedOn.get())));
        }
        for (LabourEntry entry : input.labour()) {
            if (entry.people() < 1) {
                throw new BadInputException(
                        "a labour line needs at least one person",
                        Details.of("sheet", input.id(), "rate", entry.rateId()));
            }
            checkHours(entry.hours(), "labour hours");
            checkHours(entry.overtimeHours(), "labour overtime hours");
        }
        for (PlantEntry entry : input.plant()) {
            checkHours(entry.hours(), "plant hours");
            checkHours(entry.overtimeHours(), "plant overtime hours");
            checkHours(entry.standingHours(), "plant standing hours");
            checkHours(
                    entry.hours() + entry.overtimeHours() + entry.standingHours(),
                    "a plant line's working and standing hours");
        }
        if (input.labour().isEmpty()
                && input.plant().isEmpty()
                && input.materials().isEmpty()) {
            throw new BadInputException(
                    "a daywork sheet with nothing on it is not a sheet", Details.of("sheet", input.id()));
        }
        return new DayworkSheet(
                input.id(),
                input.reference(),
                input.packageId(),
                input.instruction(),
                input.workedOn(),
                signedOn,
                signedBy,
                input.labour(),
                input.plant(),
                input.materials());
    }

    /** Signed on or before the cut-off. A sheet signed later is next month's. */
    public boolean isSignedAt(LocalDate asAt) {
        return signedOn.map(on -> !on.isAfter(asAt)).orElse(false);
    }

    /**
     * Price this sheet.
     *
     * <p>Each of the three heads is totalled, then its own percentage addition goes on top, then
     * the three are added. Applying one blended addition to the total gives a different and larger
     * answer, which is the mistake this ordering is written to avoid.
     */
    public Value value(DayworkSchedule schedule) {
        Currency currency = schedule.currency();

        Money labourValue = Money.sum(
                labour.stream()
                        .map(entry -> {
                            DayworkRate rate = schedule.requireLabourRate(entry.rateId());
                            Money one = DayworkSchedule.hoursValue(
                                    rate, entry.hours(), entry.overtimeHours(), schedule.overtimeFactor());
                            return Money.sum(Collections.nCopies(entry.people(), one), currency);
                        })
                        .toList(),
                currency);

        Money plantValue = Money.sum(plant.stream().map(schedule::plantValue).toList(), currency);

        Money materialsValue =
                Money.sum(materials.stream().map(MaterialEntry::value).toList(), currency);

        Money withLabour =
                DayworkSchedule.withAddition(labourValue, schedule.additions().labour());
        Money withPlant =
                DayworkSchedule.withAddition(plantValue, schedule.additions().plant());
        Money withMaterials = DayworkSchedule.withAddition(
                materialsValue, schedule.additions().materials());
        return new Value(
                withLabour, withPlant, withMaterials, withLabour.plus(withPlant).plus(withMaterials));
    }

    /** Everything signed by a cut-off, priced and added. */
    public static Money dayworkValue(DayworkSchedule schedule, List<DayworkSheet> sheets, LocalDate asAt) {
        List<DayworkSheet> signed =
                sheets.stream().filter(sheet -> sheet.isSignedAt(asAt)).toList();
        if (signed.isEmpty()) {
            return schedule.zero();
        }
        return Money.sum(
                signed.stream().map(sheet -> sheet.value(schedule).total()).toList(), schedule.currency());
    }

    /** Sheets that are still waiting on a signature, for the chase list. */
    public static List<DayworkSheet> unsigned(List<DayworkSheet> sheets, LocalDate asAt) {
        return sheets.stream()
                .filter(sheet -> !sheet.isSignedAt(asAt) && !sheet.workedOn().isAfter(asAt))
                .toList();
    }

    /** {@code DW/2025/014 worked 2025-03-12, signed 2025-03-13}, like a register line. */
    public String describe() {
        String state = signedOn.map(on -> "signed " + SiteDates.format(on)).orElse("unsigned");
        return reference + " worked " + SiteDates.format(workedOn) + ", " + state;
    }
}
