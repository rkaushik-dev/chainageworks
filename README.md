# chainageworks

Progress measurement and interim valuation for linear civil works.

The job this does is the one a quantity surveyor does every month on a
measured-term civil contract: work out how much of the permanent works is
actually in the ground at the cut-off date, price it against the bill that was
in force on that date, and turn that into an interim valuation.

Everything is keyed on **chainage**, the running distance along the alignment,
because that is how a road, a pipeline or a rail formation is described on site.

## Status

Early. The chainage arithmetic and the bill of quantities are the parts being
built first, because nothing else can be measured until those two agree.

## Running it

```
npm install
npm test
npm run typecheck
npm run lint
```

## Layout

```
src/     the engine, no I/O below the store and http layers
tests/   unit, integration and invariant suites
```
