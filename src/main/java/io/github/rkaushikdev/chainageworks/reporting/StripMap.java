package io.github.rkaushikdev.chainageworks.reporting;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import io.github.rkaushikdev.chainageworks.progress.StepProgressDetail;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A strip of the alignment, one row per step, showing where each step has got to and what is
 * stopping it.
 *
 * @param packageId the package drawn
 * @param extent the stretch it covers
 * @param cellLength millimetres of alignment to a cell
 * @param rows one row per step of the rule of credit
 */
public record StripMap(String packageId, ChainageRange extent, long cellLength, List<Row> rows) {

    /** Fifty metres a cell, which puts a kilometre across twenty characters. */
    public static final long DEFAULT_CELL_LENGTH = 50 * Stations.MM_PER_METRE;

    public StripMap {
        rows = List.copyOf(rows);
    }

    /**
     * One cell of a row.
     *
     * @param extent the stretch the cell covers
     * @param state what that stretch of the step looks like
     */
    public record Cell(ChainageRange extent, CellState state) {

        /** {@code 12+100.000 to 12+200.000 withheld}, for a chase list. */
        public String describe() {
            return extent.format() + " " + state.wire();
        }
    }

    /**
     * One step along the package.
     *
     * @param stepId the step
     * @param cells the cells from the start of the package to its end
     */
    public record Row(String stepId, List<Cell> cells) {

        public Row {
            cells = List.copyOf(cells);
        }

        /** The glyph of every cell, start to end. */
        public String glyphs() {
            return cells.stream().map(c -> c.state().glyph()).collect(Collectors.joining());
        }
    }

    /**
     * How the strip is drawn.
     *
     * @param labelWidth the width of the step name column; empty sizes it to the longest step name,
     *     never under four
     * @param scale print the chainage of both ends underneath
     */
    public record RenderOptions(Optional<Integer> labelWidth, boolean scale) {

        /** Labels as wide as the longest step name, with the scale underneath. */
        public static final RenderOptions DEFAULT = new RenderOptions(Optional.empty(), true);

        public RenderOptions {
            labelWidth = labelWidth == null ? Optional.empty() : labelWidth;
        }

        public RenderOptions withLabelWidth(int other) {
            return new RenderOptions(Optional.of(other), scale);
        }

        public RenderOptions withScale(boolean other) {
            return new RenderOptions(labelWidth, other);
        }
    }

    /** The strip in fifty metre cells. */
    public static StripMap build(ProgressContext context, WorkPackage pkg, LocalDate asAt) {
        return build(context, pkg, asAt, DEFAULT_CELL_LENGTH);
    }

    /**
     * The strip in cells of {@code cellLength} millimetres. The last cell stops at the end of the
     * package, so it can be shorter than the rest.
     *
     * @throws BadInputException when the cell length is not positive
     */
    public static StripMap build(ProgressContext context, WorkPackage pkg, LocalDate asAt, long cellLength) {
        if (cellLength <= 0) {
            throw new BadInputException(
                    "a strip cell has to be a positive whole number of millimetres",
                    Details.of("cellLength", cellLength));
        }
        List<ChainageRange> cells = cellsOf(pkg.extent(), cellLength);
        List<Row> rows = StepProgressDetail.forPackage(context, pkg, asAt).stream()
                .map(step -> new Row(
                        step.stepId(),
                        cells.stream()
                                .map(cell -> new Cell(cell, stateOf(cell, step)))
                                .toList()))
                .toList();
        return new StripMap(pkg.id(), pkg.extent(), cellLength, rows);
    }

    private static List<ChainageRange> cellsOf(ChainageRange extent, long cellLength) {
        List<ChainageRange> out = new ArrayList<>();
        for (long from = extent.from(); from < extent.to(); from += cellLength) {
            out.add(new ChainageRange(from, Math.min(from + cellLength, extent.to())));
        }
        return out;
    }

    private static CellState stateOf(ChainageRange cell, StepProgressDetail step) {
        if (overlaps(cell, step.withheldExtent()) && overlaps(cell, step.builtExtent())) {
            return CellState.WITHHELD;
        }
        if (overlaps(cell, step.creditedExtent())) {
            return CellState.CREDITED;
        }
        if (overlaps(cell, step.builtExtent())) {
            return CellState.HELD;
        }
        return CellState.NOT_STARTED;
    }

    private static boolean overlaps(ChainageRange cell, List<ChainageRange> ranges) {
        return !ChainageRange.intersectLists(List.of(cell), ranges).isEmpty();
    }

    /** The strip drawn with the default options. */
    public String render() {
        return render(RenderOptions.DEFAULT);
    }

    /** A fixed width picture of the strip, for the terminal and the report. */
    public String render(RenderOptions options) {
        int labelWidth = options.labelWidth()
                .orElseGet(() -> Math.max(
                        rows.stream().mapToInt(r -> r.stepId().length()).max().orElse(0), 4));
        List<String> lines = new ArrayList<>(rows.size() + 1);
        for (Row row : rows) {
            lines.add(Text.padEnd(row.stepId(), labelWidth) + " " + row.glyphs());
        }
        if (!options.scale()) {
            return String.join("\n", lines);
        }
        int width = rows.isEmpty() ? 0 : rows.get(0).cells().size();
        String left = Stations.format(extent.from(), 0, false);
        String right = Stations.format(extent.to(), 0, false);
        int gap = Math.max(1, width - left.length() - right.length());
        lines.add(" ".repeat(labelWidth) + " " + left + " ".repeat(gap) + right);
        return String.join("\n", lines);
    }

    /** Cells in a state, with where they are, for a chase list. */
    public List<Cell> cellsInState(CellState state) {
        return rows.stream()
                .flatMap(row -> row.cells().stream().filter(c -> c.state() == state))
                .toList();
    }

    /**
     * The chainage the cell at {@code index} starts at.
     *
     * @throws BadInputException when there is no cell at that index
     */
    public long stationAtCell(int index) {
        if (rows.isEmpty() || index < 0 || index >= rows.get(0).cells().size()) {
            throw new BadInputException("no cell at that index", Details.of("index", index));
        }
        return rows.get(0).cells().get(index).extent().from();
    }
}
