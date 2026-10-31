# Hub Walker Compatibility Preparation

Prepared 2026-10-31 on `codex/walker-api-synthesis-20261031`, based on Hub main
`0b017fdea8ab94added7bf65a626263e4fa18337`. This branch contains reports/docs only;
no plugin source, build configuration, dependency, installed JAR or client changes.

Client counterpart: sibling checkout `Microbot-walker-api`, same branch name;
read its `docs/walker-api-synthesis/API-INVENTORY.md` and `HANDOFF.md` first.

## Reports

- [Exact source locations](inventory/consumers.csv): committed Hub/client consumer
  records with commit and blob provenance.
- [Call digest](inventory/call-summary.csv): grouped static-owner call counts.
- [Candidate availability](inventory/call-availability.csv): arity screening only,
  not type resolution, binary linkage or successful compilation.

Hub main is scanned in full for matching production Java files. The 17 additional
private/experimental Hub branches are changed-file snapshots relative to main.
They are not unique additive call totals; zero delta records does not mean no
inherited walker calls. Client variants are labelled separately. Dirty working-tree
edits are deliberately excluded, including Shared Automation's existing docs.

## Highest-Priority Compatibility Checks

1. Preserve legacy Rs2Walker and Rs2GameObject APIs used by main. Main has 167
   two-argument walkTo call sites across 80 files and 163 one-argument sites across
   71 files; counts are lexical calls, not resolved overloads or unique plugins.
2. The private `codex/mahogany-interaction-api-20261020` snapshot
   `97be5d6d627e6e525535bb2f3341a0bfb2ea92d0` uses InteractionRequest factories and
   cancelInteraction, available on runtime/data-sync but not canonical playable.
   Its walkTo(WorldPoint,InteractionRequest) can appear arity-compatible with
   canonical walkTo(WorldPoint,int). It is not compatible. Homes is historical
   source evidence only: do not launch it for validation.
3. Reconcile client interaction API before rewriting shared callers. Retain one
   request per operation, cancel abandoned requests and require observed interaction
   effects, not just coordinate arrival/readiness.
4. Preserve active Hunter, blackjacking, Tithe, Agility and other private feature
   branches. This preparation does not advance or clean them and changes no dirty
   shared-utilities documents.

## Implementation Gate

After client adapters exist, compile affected consumers against that exact local
candidate artifact using the established JDK 11 setup. Use focused generated JAR
tasks with matching pluginList properties and sequential builds per checkout.
Never silently substitute a released remote client to mask a missing local API.
Test caller cancellation/stop, ownership changes, banked routes and interactions.

No Hub application build or gameplay smoke was performed for these report-only
changes. Build, installation, enablement, restart and publication are separate steps.
The running client and active Ardougne Agility workload remain untouched.
