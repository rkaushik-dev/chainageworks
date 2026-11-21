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

## Tech stack

- Java 21, built with Maven through the wrapper (`./mvnw`, Maven 3.9.16)
- Spring Boot 3.5 with Spring Web MVC for the HTTP API
- picocli for the `cw` command line
- Jackson for the job file and the JSON answers
- JUnit 5 and AssertJ for the tests, JaCoCo for coverage (the build fails below
  80% of lines), Spotless with Palantir Java Format for formatting

There is no database. A job is one JSON file, written atomically through a
temporary file and a rename, with an optional `.bak` of the previous version.

## Building and testing

```
./mvnw -B verify          # formatting check, every test, the coverage gate, the jar
./mvnw -B verify -Pperf   # only the timing suite, against a 200 package job
./mvnw spotless:apply     # reformat before committing
```

The timing suite lives in its own profile so a slow build machine cannot fail
the ordinary build. `verify` leaves the runnable jar at
`target/chainageworks-0.1.0.jar`.

## The command line

```
java -jar target/chainageworks-0.1.0.jar --help
java -jar target/chainageworks-0.1.0.jar -f job.json status
java -jar target/chainageworks-0.1.0.jar -f job.json commands       # the whole reference, off the command tree
java -jar target/chainageworks-0.1.0.jar -f job.json month-end 3 ./out  # the whole month in one go
```

The examples below shorten that to `cw`. `-f/--file` names the job file
(default `chainageworks.json`). The commands fall into four groups.

**Look at the job.** `status`, `config`, `bill`, `packages`, `reconcile`,
`doctor`, `revisions`, `commands`.

**Load what happened.** `import-records`, `import-sign-offs`, `import-ncrs`,
`import-diary`, `import-holidays`, `import-applications`, `import-notices`,
`import-pay-less`, `import-returns`, `import-indices`.

**Value it.** `progress`, `certificate`, `outturn`, `variations`, `payments`,
`output`, `risks`, `delay`, `defects`, `strip`, `curve`, `cash`, `lookahead`,
`haul`.

**Get it out.** `export`, `csv`, `month-end`.

Every report command takes a valuation number and answers as at that cut-off.
`--json` prints machine-readable output and `--markdown` prints a document.
A run exits 0 when it worked, 1 when the input or the job was refused (the
reason goes to stderr as `<code>: <message>`), and 2 on an internal failure.

## The API

```
CHAINAGEWORKS_JOB_FILE=job.json java -jar target/chainageworks-0.1.0.jar serve
curl localhost:8730/routes
curl localhost:8730/valuations/3/certificate
```

The server refuses to start on a job that cannot be valued at all, and says so
on stderr rather than answering five hundreds. A job with a dangling reference
still serves, because most of its numbers are still right; `/doctor` says which
ones are not.

Every answer is JSON and carries an `x-request-id` header. A failure answers
with a problem body, `{"code": ..., "message": ..., "details": {...}}`. A path
nobody serves is a 404, and a method a path does not take is a 405 with an
`Allow` header.

| method | path | what it answers |
| --- | --- | --- |
| GET | `/health` | `{"status":"ok"}` |
| GET | `/routes` | every route and the methods it takes |
| GET | `/job` | the job settings and what each register holds |
| GET | `/schedule?periods=` | the valuation periods |
| GET | `/bill?asAt=` | the bill in force on a date |
| GET | `/packages`, `/packages/{id}` | the work package register, one package |
| GET | `/records?asAt=&package=` | the survey records in effect on a date |
| GET | `/doctor` | references that go nowhere, and what blocks a valuation |
| GET | `/valuations/{number}/progress` | the progress report |
| GET | `/valuations/{number}/certificate` | the payment certificate |
| GET | `/valuations/{number}/delay` | the extension of time position (204 without completion terms) |
| GET | `/valuations/{number}/defects` | the defects position (204 without a defects period) |
| GET | `/valuations/{number}/payments` | the subcontract payments and the notice diary |
| GET | `/valuations/{number}/output` | output against the hours booked |
| GET | `/valuations/{number}/outturn` | the anticipated final account |
| GET | `/valuations/{number}/variations` | the variation register, valued |
| GET | `/valuations/{number}/risks` | the early warning register |
| GET | `/valuations/{number}/strip/{packageId}` | where each step of a package has got to |
| GET | `/lookahead?from=&weeks=&gangs=` | what the next few weeks have to get through |
| GET | `/haul` | the haul plan and what moving the material costs |
| GET | `/revisions` | what each bill revision did to the one before it |
| GET | `/cash?periods=&late=` | the cash forecast |
| GET | `/curve?periods=` | planned against earned, period by period |
| POST | `/import/{records,sign-offs,ncrs,diary,applications,notices,returns,indices,pay-less}?partial=true` | load a CSV export; 200 when every row loaded, 207 when `partial` let some rows be rejected |

## Configuration

The environment overrides the defaults; command line flags override both.

| variable | default |
| --- | --- |
| `CHAINAGEWORKS_JOB_FILE` | `chainageworks.json` |
| `CHAINAGEWORKS_HOST` / `CHAINAGEWORKS_PORT` | `127.0.0.1` / `8730` |
| `CHAINAGEWORKS_LOG_LEVEL` | `info` |
| `CHAINAGEWORKS_AUTO_SAVE` | yes |
| `CHAINAGEWORKS_STRICT` | no (yes refuses a progress report when the register does not reconcile) |
| `CHAINAGEWORKS_PERIODS`, `CHAINAGEWORKS_LOOKAHEAD_WEEKS`, `CHAINAGEWORKS_GANGS`, `CHAINAGEWORKS_WORST_LINES` | 12, 4, 1, 10 |

`cw config` prints the settings a run would use.

## Layout

```
src/main/java/io/github/rkaushikdev/chainageworks/
  domain/       the contract: chainage, money, quantities, the bill, time
  progress/     what was built and what may be credited for it
  valuation/    what it is worth: certificates, retention, cash, the outturn
  reporting/    text, markdown and csv renderings, no arithmetic
  ingest/, io/  csv readers for the site and commercial exports
  store/        the job, its registers and the file format
  config/       the settings and the environment they are read from
  app/          what the cli and the api both call
  http/         the read-mostly api
  cli/          the command tree
  util/         number text, white space, patterns and JSON writing
src/test/java/  unit, integration, invariant and performance suites
```

The public types are the supported surface. `PublicApiTest` carries its own
list of them, so adding or removing one is an edit somebody has to make twice.

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
