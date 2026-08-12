# Maintainer Workflow

This document describes the normal maintenance and release workflow for
`bbottema/java-socks-proxy-server` (`com.github.bbottema:java-socks-proxy-server`).
The repository uses a single long-lived `master` branch, Maven, and CircleCI.

## 1. Interpret the request

Decide whether the request is issue triage, implementation without a release,
release preparation, or an explicitly authorized release. Never infer release
authorization from words such as "patch" when they only describe a code diff.
An explicit request to publish a patch, minor, major, or as-is version remains
authorization through the corresponding CircleCI approval and GitHub release.

Do not create tags or change the POM version by hand during ordinary release
preparation. The CircleCI release jobs own version changes, deployment, and tags.

## 2. Start from live state

```powershell
git status --short --branch
git fetch --prune --tags origin
git branch -vv
gh auth status
```

Work only from a clean `master` aligned with `origin/master`. Use a topic branch
when review is required; release automation still runs from `master`. Never
rewrite shared history to resolve divergence.

The supported source and bytecode baseline is Java 8. Verify the effective
toolchain before implementation or release work:

```powershell
java -version
mvn -q help:evaluate '-Dexpression=maven.compiler.source' -DforceStdout
```

## 3. Triage issues and pull requests

Read the live issue or pull request, comments, labels, milestone, and linked
commits before editing. Re-read labels immediately before changing them.

Use the repository's canonical labels. Added functionality uses either
`enhancement` or `major feature`, never both. Other work types are `bug`,
`maintenance`, `documentation`, `security`, `dependencies`, and
`3rdparty-problem`. Priority labels are `Priority-Low`, `Priority-Medium`, and
`Priority-High`. Workflow and disposition labels include `duplicate`, `invalid`,
`question`, `need-user-input`, `needs-research`, `postponed indefinitely`,
`will close soon`, `wontfix`, and `help wanted`; `java` identifies Java update
pull requests.

Preserve genuinely distinct project labels. When replacing an alias, migrate its
issues and pull requests to the canonical label before deleting or renaming it.
Remove stale workflow labels when their state is no longer true.

Issue titles describe the outcome directly. Do not prefix new titles with
`Bug:`, `Feature:`, `[maintenance]`, or similar type markers; labels carry type.

## 4. Release milestones

Every semantic-version tag has one milestone whose title is the exact numeric
version without a `v` prefix. Its description is empty, its due date is the
actual historical release date at UTC midnight, and it is closed after every
member is closed.

Assign every issue and merged pull request represented by a release to that
version's milestone. Do not include rejected, superseded, or unrelated work.
GitHub exposes pull requests through the issues endpoint for milestone updates.

```powershell
gh api 'repos/bbottema/java-socks-proxy-server/milestones?state=all&per_page=100' --paginate
gh api --method PATCH repos/bbottema/java-socks-proxy-server/issues/NUMBER -F milestone=MILESTONE_NUMBER
```

When creating or repairing historical milestones, derive the date from the
release notes or annotated tag date. PATCH `due_on` explicitly after creation or
closure and verify the raw API value is exactly `YYYY-MM-DDT00:00:00Z`.

## 5. Implement and verify

Read the affected implementation and tests first. Preserve SOCKS4/5 protocol
framing, authentication negotiation, socket shutdown, and the behavior of both
`SocksServer` and `SyncSocksServer`. Treat lifecycle, concurrency, timeout, and
JUnit-extension changes as compatibility-sensitive.

Run focused tests first, then the same verification used by CircleCI:

```powershell
mvn -Dtest=SomeTest test
mvn verify -Dmaven.javadoc.skip=true -Djacoco.skip=true -Dlicense.skip=true
```

Before committing, inspect both the unstaged and staged diff. Do not mix
unrelated dependency, formatting, documentation, or release-note changes.

## 6. Documentation and repository release notes

Update `README.md` when coordinates, the current version, public usage, or
compatibility guidance changes. `RELEASE.txt` is the full repository release
history and must remain newest-first and factual. `how to release.txt` documents
the operational release entry point; keep it consistent with this workflow.

Release notes should emphasize behavior users can observe. Skip internal build
details unless they affect published artifacts. A patch can be concise, but its
GitHub release body must still stand alone.

## 7. Commit and push

Stage selectively and use a semantic subject:

```powershell
git add <paths>
git diff --cached --check
git diff --cached --stat
git commit -m "fix(scope): concise summary"
git push origin master
```

Use `[skip ci]` for bookkeeping-only documentation when appropriate. Do not use
it for code, dependency, build, or release-lane changes that require validation.

## 8. Release through CircleCI

Release only with explicit authorization. Push the fully verified candidate to
`master`, then approve exactly one CircleCI gate:

- `approve-deploy-patch-version`
- `approve-deploy-minor-version`
- `approve-deploy-major-version`
- `approve-deploy-as-is-version`

The deployment publishes to Maven Central and creates the release commit and
numeric tag. If Maven Central publication succeeds but repository updates fail,
confirm that immutable external state before repairing the version commit or tag.
Never create a second Central deployment for the same version.

After deployment, fetch `master` and tags, verify the Maven Central artifact,
and confirm the local branch can be fast-forwarded to `origin/master`.

## 9. GitHub releases

Every semantic-version tag has exactly one published, non-prerelease GitHub
release. Keep the numeric tag unchanged and title the release `vVERSION`.
Preserve assets already attached to a release.

Each body must be a permanent, tag-specific record that:

- states what changed in that version;
- links useful issues, pull requests, or exact commits;
- explains source, binary, runtime, or configuration compatibility;
- does not delegate essential meaning to mutable branch documentation; and
- omits test evidence and internal release-process commentary.

Create one release even for a no-functional-change tag; say explicitly that API,
dependencies, and behavior are unchanged. Never fold several tags into one
GitHub release.

## 10. Historical bookkeeping

For an existing tag, reconstruct facts from the tag range, release history,
issues, and pull requests. Create a retrospective issue only for a coherent,
substantive bug, security fix, feature, or user-visible enhancement that lacks a
record. Its body must link exact commit evidence and identify the first release.
Apply canonical labels and the actual release milestone, close it, and avoid
duplicating an existing issue or pull request. Do not invent issues for version
bumps, formatting, routine build work, or ordinary dependency updates.

Historical GitHub releases may be published today, but their milestones retain
the original release dates.

## 11. Definition of done

- Required tests pass, or any omission is explained.
- `master` is clean and aligned with `origin/master`.
- Relevant issues and merged pull requests have canonical labels and the actual
  release milestone.
- Every semantic-version tag has a closed, dated, empty-description milestone.
- Every semantic-version tag has one published, self-contained GitHub release.
- The repository release notes and current-version documentation are consistent.
