# chainageworks

Progress measurement and interim valuation for linear civil works.

The job this does is the one a quantity surveyor does every month on a
measured-term civil contract: work out how much of the permanent works is
actually in the ground at the cut-off date, price it against the bill that was
in force on that date, and turn that into an interim valuation.

Everything is keyed on **chainage**, the running distance along the alignment,
because that is how a road, a pipeline or a rail formation is described on site.

## What makes it more than a spreadsheet

Five separate rules land on the same number, and they are applied in the order
the contract applies them in:

1. the survey book says what was built,
2. the hold points and the open non-conformance reports say how much of that
   may count,
3. the rule of credit turns the step fractions into one earned fraction,
4. the bill revision in force at the cut-off prices it,
5. the measurement caps it, because nothing may be earned that was not built.

Move the cut-off by a day and any of the five can change the answer. That is
the whole point, and it is why a remeasure dated after the cut-off does not
change last month's certificate.

## Running it

```
npm install
npm test          # unit, integration and invariant suites
npm run typecheck
npm run lint
npm run test:perf # timings against a 200 package job
```

## The command line

```
npm run cli -- --help
npm run cli -- commands           # the whole reference, off the command tree
npm run cli -- month-end 3 ./out  # the whole month in one go
```

The commands fall into four groups.

**Look at the job.** `status`, `config`, `bill`, `packages`, `reconcile`,
`doctor`, `revisions`, `commands`.

**Load what happened.** `import-records`, `import-sign-offs`, `import-ncrs`,
`import-diary`, `import-holidays`, `import-applications`, `import-notices`,
`import-pay-less`, `import-returns`, `import-indices`.

**Value it.** `progress`, `certificate`, `outturn`, `variations`, `payments`,
`output`, `risks`, `delay`, `defects`, `strip`, `curve`, `cash`.

**Get it out.** `export`, `csv`, `month-end`.

Every report command takes a valuation number and answers as at that cut-off.
`--json` prints machine-readable output and `--markdown` prints a document.

## The API

```
npm run serve
curl localhost:8730/routes
curl localhost:8730/valuations/3/certificate
```

The server refuses to start on a job that cannot be valued at all, and says so
on stderr rather than answering five hundreds. A job with a dangling reference
still serves, because most of its numbers are still right; `/doctor` says which
ones are not.

## Layout

```
src/domain/     the contract: chainage, money, quantities, the bill, time
src/progress/   what was built and what may be credited for it
src/valuation/  what it is worth: certificates, retention, cash, the outturn
src/reporting/  text, markdown and csv renderings, no arithmetic
src/ingest/     csv readers for the site and commercial exports
src/store/      the job, its registers and the file format
src/app/        what the cli and the api both call
src/http/       the read-mostly api
src/cli/        the command tree
tests/          unit, integration, invariant and performance suites
```

`src/index.ts` is the public surface. Anything not exported there is free to
change.

## Conventions worth knowing before reading the code

- Money is scaled integers in minor units. Quantities are scaled integers in
  thousandths. Nothing anywhere is a float that has to add up.
- A site date is a day, never an instant. Cut-offs, sign-offs and diary entries
  are all dated and none of them is timed.
- Chainage ranges are half-open, and the alignment carries chainage equations,
  so the through-chainage can contain both overlaps and gaps.
- Every report is asked for as at a date. Nothing defaults to today, because a
  report that quietly means "now" cannot be compared with the one somebody
  printed last week.
