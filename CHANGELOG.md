# Changelog

## Version 1.5.0

(December 3, 2019)

-   Official release of Matrix for Declarative
-   [JENKINS-49500](https://issues.jenkins-ci.org/browse/JENKINS-49500) - Update only changed Job Properties @gulyaev13 (#365)
-   [JENKINS-60217](https://issues.jenkins-ci.org/browse/JENKINS-60217) - Support comparator pattern for the changeset directive @v1v (#366)
-   [JENKINS-60115](https://issues.jenkins-ci.org/browse/JENKINS-60115) - Make toGroovy() and toJSON() @Nonnull @bitwiseman (#363)
-   Update plugins.jenkins.io GitHub links @MarkEWaite (#364)

## Version 1.4.0

(November 4, 2019)

-   [JENKINS-59115](https://issues.jenkins-ci.org/browse/JENKINS-59115) -
    Support comparator for the when branch @v1v (#351)
-   [JENKINS-37984](https://issues.jenkins-ci.org/browse/JENKINS-37984) -
    Mitigate "Method code too large! error"  @bitwiseman (#355)
-   [JENKINS-51865](https://issues.jenkins-ci.org/browse/JENKINS-51865) -
    Introduce beforeOptions in 'when' @famod (#356)
-   [JENKINS-47703](https://issues.jenkins-ci.org/browse/JENKINS-47703) -
    allow building dockerfile on Windows @casz (#354)
-   [JENKINS-54322](https://issues.jenkins-ci.org/browse/JENKINS-54322) -
    enable markStageSkippedForConditional within the scripted @v1v (#346)
-   Remove call to System.err.println in ModelInterprer.groovy @dwnusbaum (#334)

## Version 1.3.9

(June 7, 2019)

-   [JENKINS-57162](https://issues.jenkins-ci.org/browse/JENKINS-57162)
    - `agent dockerfile`  name/hash should incorporate build args.
-   [JENKINS-57826](https://issues.jenkins-ci.org/browse/JENKINS-57826)
    - `post`  on a `stage`  should handle `catchError` 's `stageResult` 
    as distinct from the status of the whole build.

## Version 1.3.8
(April 15, 2019)

-   [JENKINS-46354](https://issues.jenkins-ci.org/browse/JENKINS-46354) -
    Don't skip all stages when retrying from top level.

## Version 1.3.7
(March 20, 2019)

-   [JENKINS-52850](https://issues.jenkins-ci.org/browse/JENKINS-52850) -
    Support SSH private key credentials in `environment`.
-   [JENKINS-56402](https://issues.jenkins-ci.org/browse/JENKINS-56402) -
    Set build result to the logical result in post conditions.
-   [JENKINS-56544](https://issues.jenkins-ci.org/browse/JENKINS-56544)
    - `failFast` option for parallel stages no longer sets build status
    to `ABORTED` when other stages are aborted before entering their
    step execution.

## Version 1.3.6
(March 6, 2019)

-   [JENKINS-56405](https://issues.jenkins-ci.org/browse/JENKINS-56405) -
    Don't require registry credentials if a registry URL is specified.

## Version 1.3.5
(March 1, 2019)

-   [JENKINS-52744](https://issues.jenkins-ci.org/browse/JENKINS-52744) -
    Show a meaningful error when an environment variable value
    references an undefined variable.
-   [JENKINS-49950](https://issues.jenkins-ci.org/browse/JENKINS-49950) -
    Allow using Dockerhub credentials without having to specify a
    registry URL.
-   [JENKINS-55476](https://issues.jenkins-ci.org/browse/JENKINS-55476) -
    Don't trigger `unsuccessful` post condition for successful builds.
-   [JENKINS-55459](https://issues.jenkins-ci.org/browse/JENKINS-55459) -
    Make parallel stages that fail fast result in the build being marked
    as failed, not aborted.

## Version 1.3.4.1
(January 8, 2019)

-   [Fix security
    vulnerability](https://jenkins.io/security/advisory/2019-01-08/)

## Version 1.3.4
(December 10, 2018) 

-   [JENKINS-49997](https://issues.jenkins-ci.org/browse/JENKINS-49997) -
    Add new `unsuccessful` `post` condition
-   [JENKINS-53734](https://issues.jenkins-ci.org/browse/JENKINS-53734) -
    Allow use of `parallel` within top-level sequential stages. 
-   [JENKINS-54919](https://issues.jenkins-ci.org/browse/JENKINS-54919) -
    Change processing of `agent` contents to allow references to newly
    defined parameter default values.
-   [JENKINS-46894](https://issues.jenkins-ci.org/browse/JENKINS-46894) -
    Add a new `when` condition for checking the build's cause.

## Version 1.3.3
(November 27, 2018) 

-   Add REST API for Stage Restart functionality
-   [JENKINS-54047](https://issues.jenkins-ci.org/browse/JENKINS-54047) -
    Fix handling of `!` expressions in `environment` variable
    resolution.
-   [JENKINS-48556](https://issues.jenkins-ci.org/browse/JENKINS-48556) -
    Prevent sporadic failure to recognize block-scoped steps, such
    as `timestamps`, as valid `options`.
-   [JENKINS-50880](https://issues.jenkins-ci.org/browse/JENKINS-50880) -
    Add `beforeInput` flag for `when.`
-   [JENKINS-53558](https://issues.jenkins-ci.org/browse/JENKINS-53558) -
    New `option` for setting `failFast` for all parallel stages in a
    Pipeline.

## Version 1.3.2
(August 31, 2018) 

-   [JENKINS-51027](https://issues.jenkins-ci.org/browse/JENKINS-51027) -
    Include `post` conditions in Directive Generator correctly.
-   [JENKINS-51932](https://issues.jenkins-ci.org/browse/JENKINS-51932) -
    Include `isRestartedRun` in `when` Directive Generator.
-   [JENKINS-51931](https://issues.jenkins-ci.org/browse/JENKINS-51931) -
    Fix NPE if a `when` condition like `allOf` or `anyOf` has no
    children.
-   [JENKINS-51872](https://issues.jenkins-ci.org/browse/JENKINS-51872) -
    Fix `tool` dropdowns in Directive generator.
-   [JENKINS-51383](https://issues.jenkins-ci.org/browse/JENKINS-51383) -
    Properly catch/handle `Throwable` as well as `Exception` in stage
    execution.
-   [JENKINS-51227](https://issues.jenkins-ci.org/browse/JENKINS-51227) -
    Add `quietPeriod` Declarative option.
-   [JENKINS-53316](https://issues.jenkins-ci.org/browse/JENKINS-53316) -
    Switch to depending on `jackson2-api` plugin rather than shading
    everything Jackson/JSON-related ourselves.
-   [JENKINS-52114](https://issues.jenkins-ci.org/browse/JENKINS-52114) -
    Make `post` `failure` and `success` for a `stage` care about whether
    that `stage` has an error, rather than the full build's status. This
    allows a `stage` within a `parallel` that does not have any errors
    but completes after another `parallel` `stage` that does have an
    error (resulting in the build's status now being `FAILURE`) to fire
    its `post` `success` condition as would be expected.
    -   **NOTE**: this does result in a small change in expected
        behavior. If you are setting `currentBuild.result` to `FAILURE`
        in a `stage` and then have a `post` `failure` condition for that
        same `stage`, without ever actually having a true error or step
        failure in the `stage`, the `post` `failure` for the `stage` in
        question will not fire. `post` `failure` conditions on a `stage`
        now fire when there's an error in the execution of the `stage`
        only, and `post` `success` conditions on a `stage` fire whenever
        there is no error in the execution of the `stage`.
    -   No change in behavior for any other `post` condition, or
        for `post` outside of `stages` (including `success`
        and `failure`) is introduced by this.

## Version 1.3.1
(June 27, 2018) 

-   [JENKINS-51962](https://issues.jenkins-ci.org/browse/JENKINS-51962) -
    Propagate failures in nested sequential stages to top-level
    properly.
-   [JENKINS-52084](https://issues.jenkins-ci.org/browse/JENKINS-52084) -
    Execute `post` for sequential stage parent in proper context (such
    as `agent`)

## Version 1.3
(June 14, 2018)

-   [JENKINS-50882](https://issues.jenkins-ci.org/browse/JENKINS-50882) -
    fix Directive Generator link from multibranch folders.
-   [JENKINS-50964](https://issues.jenkins-ci.org/browse/JENKINS-50964) -
    Fix 404 on jenkins.io Pipeline Syntax link 
-   [JENKINS-50645](https://issues.jenkins-ci.org/browse/JENKINS-50645) -
    Switch to firing `failure` after `success` and `unstable`, in case
    those `post` conditions modify status.
-   [JENKINS-50815](https://issues.jenkins-ci.org/browse/JENKINS-50815) -
    Fall back to `GIT_LOCAL_BRANCH` for `when branch` condition
    if `BRANCH_NAME` is not available.
-   [JENKINS-43016](https://issues.jenkins-ci.org/browse/JENKINS-43016) -
    Convert empty string `label` for `agent` to `agent any` in JSON.
-   [JENKINS-46809](https://issues.jenkins-ci.org/browse/JENKINS-46809) -
    Add support for nesting sequential groups of `stages` inside
    a `stage`.
-   [JENKINS-45455](https://issues.jenkins-ci.org/browse/JENKINS-45455) -
    Allow restarting a completed build from any stage which ran in that
    build, with all inputs (SCM, parameters, etc) preserved, and if the
    new `preserveStashes` option has been specified, any stashes from
    the original build available to the new build.
-   [JENKINS-44993](https://issues.jenkins-ci.org/browse/JENKINS-44993) -
    Don't swallow errors in `post`.

## Version 1.2.9
(Apr 17, 2018)

-   [JENKINS-50652](https://issues.jenkins-ci.org/browse/JENKINS-50652) -
    Don't fire `failure` for aborted/timed-out `sh` and `bat` steps.
-   [JENKINS-41239](https://issues.jenkins-ci.org/browse/JENKINS-41239) -
    Add new `cleanup` `post` condition to always run, after every
    other `post` condition has run.
-   [JENKINS-50833](https://issues.jenkins-ci.org/browse/JENKINS-50833) -
    Revert `DeclarativeAgentDescriptor.all()` signature to unbreak
    Pipeline Editor.
-   [JENKINS-50682](https://issues.jenkins-ci.org/browse/JENKINS-50682) -
    Fix `when` conditions `tag` and `changeRequest` in Directive
    Generator.

## Version 1.2.8
(Apr 5, 2018)

-   [JENKINS-49677](https://issues.jenkins-ci.org/browse/JENKINS-49677) -
    Better uniqueness on Dockerfile tag name.
-   [JENKINS-49226](https://issues.jenkins-ci.org/browse/JENKINS-49226) -
    Add new `equals` condition for `when`.
-   [JENKINS-49976](https://issues.jenkins-ci.org/browse/JENKINS-49976) -
    Prevent serialization warnings related to `ModelASTValue` inner
    classes.
-   [JENKINS-41060](https://issues.jenkins-ci.org/browse/JENKINS-41060) -
    Add new `fixed` and `regression` `post` conditions.
-   [JENKINS-49558](https://issues.jenkins-ci.org/browse/JENKINS-49558) -
    Add new `newContainerPerStage` option, which will spin up a fresh
    container of a `docker` or `dockerfile` top-level agent for each
    stage, rather than reusing the same container across all stages.
-   [JENKINS-48523](https://issues.jenkins-ci.org/browse/JENKINS-48523)
    - `when` conditions for checking if building a git tag or a change
    request (such as a GitHub pull request).
-   [JENKINS-47106](https://issues.jenkins-ci.org/browse/JENKINS-47106) -
    Properly support `alwaysPull` option in top-level `agent ``docker`
-   [JENKINS-49893](https://issues.jenkins-ci.org/browse/JENKINS-49893) -
    Declarative Directive Generator added.

## Version 1.2.7
(Jan 29, 2018)

-   [JENKINS-48758](https://issues.jenkins-ci.org/browse/JENKINS-48758) -
    Improve error messages around bare variables for `environment`
    values.
-   [JENKINS-48752](https://issues.jenkins-ci.org/browse/JENKINS-48752) -
    Fix `post` `change` behavior to not treat consecutive runs with any
    status but `SUCCESS` as changed.
-   [JENKINS-49070](https://issues.jenkins-ci.org/browse/JENKINS-49070) -
    Prevent use of `BigDecimal` to avoid serialization issues.

## Version 1.2.6
(Jan 12, 2018)

-   [JENKINS-48209](https://issues.jenkins-ci.org/browse/JENKINS-48209) -
    Prevent serialization problems with `when` `expression`
-   [JENKINS-48229](https://issues.jenkins-ci.org/browse/JENKINS-48229) -
    Use the agent's directory separator for `Dockerfile` path.
-   [JENKINS-48266](https://issues.jenkins-ci.org/browse/JENKINS-48266) -
    Fix execution of `post` for `parallel` stages parent.
-   [JENKINS-44461](https://issues.jenkins-ci.org/browse/JENKINS-44461) -
    Add `beforeAgent` option for `when` - if true, `when` conditions
    will be evaluated before entering the `agent`.
-   [JENKINS-48304](https://issues.jenkins-ci.org/browse/JENKINS-48304) -
    Invalidate option type caches after "extensions augmented" to
    prevent a race condition where not all plugins have loaded yet when
    the caches get initially populated.
-   [JENKINS-48380](https://issues.jenkins-ci.org/browse/JENKINS-48380) -
    add `options` for `stage` - supports block-scoped "wrappers"
    like `timeout` and Declarative options like `skipDefaultCheckout`.
-   [JENKINS-48379](https://issues.jenkins-ci.org/browse/JENKINS-48379) -
    Add `input` directive for `stage` - runs the `input` step with the
    supplied configuration before entering the `when` or `agent` for
    a `stage`, and makes any parameters provided as part of the `input`
    step available as environment variables.
-   [JENKINS-44277](https://issues.jenkins-ci.org/browse/JENKINS-44277) -
    Add `checkoutToSubdirectory(...)` Declarative option for use in
    top-level or per `stage` `options` directive.

## Version 1.2.5
(Nov 22, 2017)

-   [JENKINS-46597](https://issues.jenkins-ci.org/browse/JENKINS-46597) -
    Fix serialization errors due to `checkout` returned environment
    variables.
-   [JENKINS-47928](https://issues.jenkins-ci.org/browse/JENKINS-47928) -
    Don't run `post` failure block for skipped parallel container stages
    after previous stage failures.
-   [JENKINS-46854](https://issues.jenkins-ci.org/browse/JENKINS-46854) -
    Better validation of errors in `script` and `expression` blocks
    defined in the editor.
-   [JENKINS-48115](https://issues.jenkins-ci.org/browse/JENKINS-48115) -
    Pick `workflow-step-api` 2.14 to make sure we don't get hosed by bad
    metasteps.
-   [JENKINS-37663](https://issues.jenkins-ci.org/browse/JENKINS-37663) -
    Fix `junit` 1.22 and later `UNSTABLE` result check in `post` by
    comparing `CpsFlowExecution.getResult()` as well
    as `Run.getResult()`.
-   [JENKINS-46252](https://issues.jenkins-ci.org/browse/JENKINS-46252) -
    Mark any job that's had a Declarative run
    with `DeclarativeJobAction`.

## Version 1.2.4
(Nov 8, 2017)

-   [JENKINS-47814](https://issues.jenkins-ci.org/browse/JENKINS-47814) -
    Stop double-validating contributed validators for `post` and method
    calls
-   [JENKINS-47781](https://issues.jenkins-ci.org/browse/JENKINS-47781) -
    Truly fix the scoping of triggers, job properties, and build
    parameters


Version 1.2.3
(Nov 3, 2017) 

-   [JENKINS-46403](https://issues.jenkins-ci.org/browse/JENKINS-46403) -
    Prevent infinite loop on run start in Job DSL-created jobs.
-   [JENKINS-47421](https://issues.jenkins-ci.org/browse/JENKINS-47421) -
    Fix issue with loading running builds from earlier versions.
-   [JENKINS-46597](https://issues.jenkins-ci.org/browse/JENKINS-46597) -
    Hopefully fix serialization issue with `TreeMap.Entry`.
-   [JENKINS-47559](https://issues.jenkins-ci.org/browse/JENKINS-47559) -
    Skip further validation of non-method `when` condition, since it's
    already known to be invalid and can cause NPEs in further validation
    for no benefit.
-   [JENKINS-47600](https://issues.jenkins-ci.org/browse/JENKINS-47600) -
    Properly special-cases `VALUE = "${env.VALUE}"` in `environment` the
    same as we handle `VALUE = "${VALUE}"`
-   [JENKINS-47781](https://issues.jenkins-ci.org/browse/JENKINS-47781) -
    Narrow scope of `Describable` resolution for `triggers`, `options`,
    and `parameters`.

## Version 1.2.2
(Oct 5, 2017) 

-   [JENKINS-47202](https://issues.jenkins-ci.org/browse/JENKINS-47202) -
    Fix serialization of `environment` containing steps.
-   [JENKINS-47197](https://issues.jenkins-ci.org/browse/JENKINS-47197) -
    Make sure we don't require Java 8 at runtime.

## Version 1.2.1
(Sept 29, 2017) 

-   [JENKINS-47106](https://issues.jenkins-ci.org/browse/JENKINS-47106) -
    Switch to `alwaysPull` option for `docker`
-   [JENKINS-47109](https://issues.jenkins-ci.org/browse/JENKINS-47109) -
    Add support for `failFast` for parallel `stage`s
-   [JENKINS-47193](https://issues.jenkins-ci.org/browse/JENKINS-47193) -
    Don't break parsing of `Jenkinsfile` due to `class` or `enum`
    defined in it as well as `pipeline` block.

## Version 1.2
(Sept 21, 2017)

Declarative Pipeline runs in progress upon upgrade from versions prior
to 1.2 will probably fail on resuming.



-   [JENKINS-41334](https://issues.jenkins-ci.org/browse/JENKINS-41334) -
    Parallel definition and execution of `stage`s.
-   [JENKINS-45198](https://issues.jenkins-ci.org/browse/JENKINS-45198) -
    When on core 2.60+ with `pipeline-scm-step` 2.6+ and recent versions
    of `git`, `subversion`, or `mercurial` plugins, include SCM-provided
    variables in environment automatically.
-   [JENKINS-42753](https://issues.jenkins-ci.org/browse/JENKINS-42753) -
    Rewrite of runtime parser, fixing a bunch of issues
    with `environment`, `when expression`, variable and function
    behavior consistency, and more.
    -   Related tickets:
        -   [JENKINS-44298](https://issues.jenkins-ci.org/browse/JENKINS-44298) -
            Usage of variables and functions defined outside
            the `pipeline` block can work in `when expression` again.
        -   [JENKINS-44482](https://issues.jenkins-ci.org/browse/JENKINS-44482) -
            Pre-existing environment variables containing backslashes do
            not have their backslashes discarded.
        -   [JENKINS-44603](https://issues.jenkins-ci.org/browse/JENKINS-44603) -
            Usage of variables and functions defined outside
            the `pipeline` block work in `environment` variables again.
        -   [JENKINS-45636](https://issues.jenkins-ci.org/browse/JENKINS-45636) -
            Backslashes are correctly escaped when defined in one
            new `environment` variable and referenced in another.
        -   [JENKINS-45637](https://issues.jenkins-ci.org/browse/JENKINS-45637) -
            Environment variables containing multiple pre-existing
            environment variables no longer silently fail.
        -   [JENKINS-45916](https://issues.jenkins-ci.org/browse/JENKINS-45916) -
            Pre-existing environment variables, such as `PATH`, can be
            overridden in the `environment` directive.
        -   [JENKINS-44034](https://issues.jenkins-ci.org/browse/JENKINS-44034) -
            Variable references in `environment` variable definitions no
            longer require curly braces.
        -   [JENKINS-46112](https://issues.jenkins-ci.org/browse/JENKINS-46112) -
            Logs for `error` steps visible in Blue Ocean correctly.
        -   [JENKINS-45991](https://issues.jenkins-ci.org/browse/JENKINS-45991) - `environment` variables
            can be configured to have default values,
            i.e., `FOO = FOO ?: "default"`
-   [JENKINS-46065](https://issues.jenkins-ci.org/browse/JENKINS-46065) -
    Provide an extension point for contributing to Declarative
    validation.
-   [JENKINS-46277](https://issues.jenkins-ci.org/browse/JENKINS-46277) -
    Always do a fresh docker pull, even for agents in stages.
-   [JENKINS-46064](https://issues.jenkins-ci.org/browse/JENKINS-46064) -
    New `when` conditions for referencing the build's changelog.
-   [JENKINS-44039](https://issues.jenkins-ci.org/browse/JENKINS-44039) -
    Fixed round-tripping of single-quote multiline `script` blocks from
    the editor.
-   [JENKINS-46544](https://issues.jenkins-ci.org/browse/JENKINS-46544) -
    Give useful error message on use of bare `${...`} outside quotes.
-   [JENKINS-44497](https://issues.jenkins-ci.org/browse/JENKINS-44497) -
    Allow use of variables in `tools` values.
-   [JENKINS-46547](https://issues.jenkins-ci.org/browse/JENKINS-46547) -
    Support declaration of `pipeline` blocks in shared
    libraries' `src/*.groovy` files.

## Version 1.1.9
(July 25, 2017)

-   [JENKINS-45081](https://issues.jenkins-ci.org/browse/JENKINS-45081) -
    Stop erroring out on use of object methods named `pipeline` outside
    of a Declarative Pipeline.
-   [JENKINS-45098](https://issues.jenkins-ci.org/browse/JENKINS-45098) -
    Properly validate use of a tool without a version.
-   [JENKINS-42338](https://issues.jenkins-ci.org/browse/JENKINS-42338) -
    Make sure `tools` defined at the top-level are installed
    on `stage`-level `agent`s.

## Version 1.1.8
(July 4, 2017)

-   [JENKINS-45270](https://issues.jenkins-ci.org/browse/JENKINS-45270) -
    Fix `retry` use in `options` directive.
-   [JENKINS-43071](https://issues.jenkins-ci.org/browse/JENKINS-43071) -
    Only require `Jenkins.READ` permissions for CLI linter.

## Version 1.1.7
(June 21, 2017)

-   [JENKINS-44898](https://issues.jenkins-ci.org/browse/JENKINS-44898) -
    Workaround for JENKINS-44898 for other plugins
    implementing `WithScript` extension points until we've moved to
    Jenkins core 2.66 or later.

## Version 1.1.6
(June 12, 2017)

-   [JENKINS-44809](https://issues.jenkins-ci.org/browse/JENKINS-44809) -
    Fix problem causing duplicate `JobProperty` and related errors.

## Version 1.1.5
(June 8, 2017)

-   [JENKINS-44149](https://issues.jenkins-ci.org/browse/JENKINS-44149) -
    Properly clean up stale/defunct `JobProperty`, `Trigger`
    and `ParameterDefinition` left behind upon removal
    from `Jenkinsfile`.
-   [JENKINS-43816](https://issues.jenkins-ci.org/browse/JENKINS-43816) -
    Make sure we always have a non-null `execution` before parsing.
-   [JENKINS-43055](https://issues.jenkins-ci.org/browse/JENKINS-43055) -
    Get rid of noisy warnings regarding unset heads in Jenkins log.
-   [JENKINS-44621](https://issues.jenkins-ci.org/browse/JENKINS-44621) -
    Don't
    remove `JobProperty`, `Trigger` and `ParameterDefinition` defined
    outside of the `Jenkinsfile`.

## Version 1.1.4
(May 2, 2017)

-   [JENKINS-43339](https://issues.jenkins-ci.org/browse/JENKINS-43339) -
    Properly handle non `FAILURE` build results
    from `FlowInterruptedException`.
-   [JENKINS-43872](https://issues.jenkins-ci.org/browse/JENKINS-43872) -
    Escape dollar signs in `environment` correctly.
-   [JENKINS-43910](https://issues.jenkins-ci.org/browse/JENKINS-43910) -
    Allow use of `FileCredentials` in `environment` variables.

## Version 1.1.3
(Apr 20, 2017)

-   [JENKINS-43486](https://issues.jenkins-ci.org/browse/JENKINS-43486) -
    Handle non-String environment values properly.
-   [JENKINS-43404](https://issues.jenkins-ci.org/browse/JENKINS-43404) -
    Escaped double quotes within environment values were over-resolved.
-   [JENKINS-42748](https://issues.jenkins-ci.org/browse/JENKINS-42748) -
    Escaped backslashes in environment weren't properly escaped at
    evaluation time.

## Version 1.1.2
(Apr 5, 2017)

-   [JENKINS-42762](https://issues.jenkins-ci.org/browse/JENKINS-42762) -
    Go back to allowing multiple conditions directly in a `when`
    directive.
-   [JENKINS-42693](https://issues.jenkins-ci.org/browse/JENKINS-42693) -
    Add `additionalBuildArgs` parameter for `dockerfile`.
-   [JENKINS-42771](https://issues.jenkins-ci.org/browse/JENKINS-42771) -
    Allow + binary expressions in env values.
-   [JENKINS-43195](https://issues.jenkins-ci.org/browse/JENKINS-43195) -
    Relocate `com.github.fge.*` JSON schema classes to allow other uses
    of different versions of the library.
-   [JENKINS-41456](https://issues.jenkins-ci.org/browse/JENKINS-41456) -
    Support validation from multiple named parameters of a
    `DataBoundConstructor` with a single `Map` parameter only.
-   Brazilian Portuguese localization! Thanks to
    [kinow](https://github.com/kinow) and
    [boaglio](https://github.com/boaglio)!
-   [JENKINS-43137](https://issues.jenkins-ci.org/browse/JENKINS-43137) -
    Triple quoted strings work again in `environment`.
-   [JENKINS-43143](https://issues.jenkins-ci.org/browse/JENKINS-43143) -
    Parameters are available in `environment` values again.
-   [JENKINS-43177](https://issues.jenkins-ci.org/browse/JENKINS-43177) -
    Scrub `env.WHATEVER` in `environment` values for cross-references.
-   [JENKINS-43013](https://issues.jenkins-ci.org/browse/JENKINS-43013) -
    Round-robin resolution of `environment` values means ordering of
    declaration does not need to be relevant.
-   [JENKINS-42858](https://issues.jenkins-ci.org/browse/JENKINS-42858)
    - `credentials` environment variables are available for reference
    in `environment` values, and `environment` variable values are
    available for use in `credentials` strings as well.

## Version 1.1.1
(Mar 13, 2017)

-   1.1 was inadvertently built with Java 8 and has errors when run with
    Java 7. 1.1.1 is a rebuild of 1.1 with the correct Java 7 used.

## Version 1.1
(Mar 13, 2017)

-   [JENKINS-42230](https://issues.jenkins-ci.org/browse/JENKINS-42230) -
    Move all extension points provided by Declarative into a single new
    plugin for simpler dependencies.
-   [JENKINS-42168](https://issues.jenkins-ci.org/browse/JENKINS-42168) -
    Added `validateDeclarativePipeline` step for validating Declarative
    Pipelines from within Pipelines. Meta!
-   [JENKINS-41503](https://issues.jenkins-ci.org/browse/JENKINS-41503) -
    Fix behavior of `null` translation between JSON and Groovy
    representations.
-   [JENKINS-42286](https://issues.jenkins-ci.org/browse/JENKINS-42286) -
    Allow directory separators in `Dockerfile` file names.
-   [JENKINS-42470](https://issues.jenkins-ci.org/browse/JENKINS-42470) -
    Don't require a crumb for the `pipeline-model-converter` API
    endpoint.
-   [JENKINS-38110](https://issues.jenkins-ci.org/browse/JENKINS-38110) -
    Add a `libraries` directive for specifying shared libraries to load
    in to the build.
-   [JENKINS-41118](https://issues.jenkins-ci.org/browse/JENKINS-41118) -
    Support custom workspaces.
-   [JENKINS-42473](https://issues.jenkins-ci.org/browse/JENKINS-42473) -
    Don't use parse results from any source but the `Jenkinsfile`.
-   [JENKINS-41185](https://issues.jenkins-ci.org/browse/JENKINS-41185) -
    Add support for `anyOf`, `allOf` and `not` `when` conditions that
    contain other `when` conditions.
-   [JENKINS-42498](https://issues.jenkins-ci.org/browse/JENKINS-42498) -
    Fix `when`/`environment` serialization error when XStream
    serialization is used behind the scenes.
-   [JENKINS-42640](https://issues.jenkins-ci.org/browse/JENKINS-42640) -
    Properly handle validation of `String` -\> `int`
-   [JENKINS-42551](https://issues.jenkins-ci.org/browse/JENKINS-42551) -
    Reject `String` values in JSON that would lead to invalid Groovy
    syntax, and reject any JSON that converts to invalid Groovy syntax
    generally.
-   [JENKINS-42550](https://issues.jenkins-ci.org/browse/JENKINS-42550) -
    Properly point to bad top-level entries in validation.
-   [JENKINS-41748](https://issues.jenkins-ci.org/browse/JENKINS-41748) -
    Allow cross referencing of variables in `environment` section to
    actually work.
-   [JENKINS-41890](https://issues.jenkins-ci.org/browse/JENKINS-41890) -
    Make sure `env.WORKSPACE` can be referenced in `environment` section
    properly.

## Version 1.0.2
(Feb 21, 2017)

-   [JENKINS-42027](https://issues.jenkins-ci.org/browse/JENKINS-42027) -
    Global configuration for Declarative-specific Docker settings
    (label, registry) were not persisting across restarts.
-   [JENKINS-41668](https://issues.jenkins-ci.org/browse/JENKINS-41668) -
    Add a "dir" option for Dockerfile Declarative agent.
-   [JENKINS-41900](https://issues.jenkins-ci.org/browse/JENKINS-41900) -
    Move "should I do checkout?" logic around for simpler code in
    extensions of Declarative agents.
-   [JENKINS-41605](https://issues.jenkins-ci.org/browse/JENKINS-41605) -
    Auto-checkout from SCM in per-stage agents if they're not reusing
    the same node block as the top-level agent.
-   [JENKINS-41950](https://issues.jenkins-ci.org/browse/JENKINS-41950) -
    Properly report errors outside stages.
-   [JENKINS-41645](https://issues.jenkins-ci.org/browse/JENKINS-41645) -
    Better validation for non-binary expressions in `environment` block.
-   [JENKINS-42039](https://issues.jenkins-ci.org/browse/JENKINS-42039) -
    Add a Declarative option for "treat unstable as failure".
-   [JENKINS-42226](https://issues.jenkins-ci.org/browse/JENKINS-42226) -
    Prevent `NullPointerException` when a null value is used for `when`
    `branch` condition.

## Version 1.0.1
(Feb 10, 2017)

-   [JENKINS-41911](https://issues.jenkins-ci.org/browse/JENKINS-41911) -
    Shade JSON schema-related dependencies to avoid issues with
    conflicting library versions when certain other plugins (such as
    `jackson2-api`) are installed.

## Version 1.0
(Feb 1, 2017)

-   First non-beta release. No changes from 0.9.

## Version 0.9.1 Beta 3
(Jan 27, 2017)

-   [JENKINS-41490](https://issues.jenkins-ci.org/browse/JENKINS-41490),
    [JENKINS-41491](https://issues.jenkins-ci.org/browse/JENKINS-41491) -
    Fixing JSON support for nested tree steps and validation of certain
    tree steps.
-   [JENKINS-41518](https://issues.jenkins-ci.org/browse/JENKINS-41518) -
    Add validation of environment variable names to be valid Java
    identifiers - only relevant for JSON-\>Jenkinsfile conversion since
    this would already have shown up as a compilation error in a
    Jenkinsfile.
-   Catching a few validation fixes and string changes.

## Version 0.9.0 Beta 3
(Jan 25, 2017)

-   **FINAL BETA** - if a blocker bug is discovered before 1.0 is
    released, additional point releases may be done before 1.0.
-   [JENKINS-40984](https://issues.jenkins-ci.org/browse/JENKINS-40984) -
    Always evaluate all possible `post` conditions even if an earlier
    one fails.
-   [JENKINS-39684](https://issues.jenkins-ci.org/browse/JENKINS-39684) -
    Allow configuration of registry URL and credentials for `docker` and
    `dockerfile` `agent` types.
-   [JENKINS-40866](https://issues.jenkins-ci.org/browse/JENKINS-40866) -
    Allow per-`stage` `agent` configuration of `docker` and `dockerfile`
    to run on the same `node` as the top-level, so that you can reuse
    the workspace.
-   [JENKINS-41050](https://issues.jenkins-ci.org/browse/JENKINS-41050) -
    Perform SCM checkout on raw node first even if we're using `docker`
    or `dockerfile`.
-   [JENKINS-41243](https://issues.jenkins-ci.org/browse/JENKINS-41243) -
    Speeding up tagging of synthetic stages for improved UX in Blue
    Ocean.

## Version 0.8.2 Beta 2

-   [JENKINS-41012](https://issues.jenkins-ci.org/browse/JENKINS-41012) -
    `when` `branch` and `environment` conditions did not actually
    **work**. Fixed.
-   Fixing an issue with validation/parsing outside the context of a
    run.

## Version 0.8.1 Beta 2

-   Fixing compatibility warning to say compatible since 0.8.

## Version 0.8 Beta 2

-   [JENKINS-40418](https://issues.jenkins-ci.org/browse/JENKINS-40418) -
    Fix previously-not-running validation for triggers, parameters and
    properties.
-   [JENKINS-40337](https://issues.jenkins-ci.org/browse/JENKINS-40337) -
    Rename `properties` to `options` and add the first
    Declarative-specific `option`, `skipDefaultCheckout`.
-   [JENKINS-40462](https://issues.jenkins-ci.org/browse/JENKINS-40462) -
    Get rid of `wrappers` section, move wrappers like `timeout` and
    `retry` into `options` section.
-   [JENKINS-40580](https://issues.jenkins-ci.org/browse/JENKINS-40580) -
    Quote parallel branch names to make sure they're valid.
-   [JENKINS-40642](https://issues.jenkins-ci.org/browse/JENKINS-40642) -
    Add additional default imports so that things like `@Library` and
    `@NonCPS` work.
-   [JENKINS-40239](https://issues.jenkins-ci.org/browse/JENKINS-40239) -
    Add descriptions for build conditions.
-   [JENKINS-40393](https://issues.jenkins-ci.org/browse/JENKINS-40393) -
    Internationalize error messages!
-   [JENKINS-40524](https://issues.jenkins-ci.org/browse/JENKINS-40524) -
    Reworked `agent` syntax to be more extensible and consistent.
-   [JENKINS-40370](https://issues.jenkins-ci.org/browse/JENKINS-40370) -
    Improved `when` syntax and helpers.

## Version 0.7.1 Beta 1

-   Re-spinning release due to a mixup that resulted in one of the
    sub-plugins not ending up in the Update Center.

## Version 0.7 Beta 1

-   [JENKINS-39134](https://issues.jenkins-ci.org/browse/JENKINS-39134) -
    Fix issue with Guice and resuming a build within a `script` block.
-   [JENKINS-38153](https://issues.jenkins-ci.org/browse/JENKINS-38153) -
    Use the new `TagsAction` class to mark skipped stages so that Blue
    Ocean can render them accurately.
-   [JENKINS-39923](https://issues.jenkins-ci.org/browse/JENKINS-39923) -
    Add new `jenkins-cli` command for linting a Declarative Jenkinsfile.
-   [JENKINS-40136](https://issues.jenkins-ci.org/browse/JENKINS-40136) -
    Properly allow use of `failFast` with `parallel`.
-   [JENKINS-40226](https://issues.jenkins-ci.org/browse/JENKINS-40226) -
    Make sure non-`stage` failures still trigger `post` failure
    conditions.

## Version 0.6

-   [JENKINS-39216](https://issues.jenkins-ci.org/browse/JENKINS-39216) -
    Add `dockerfile` agent backend, auto-building a Dockerfile and
    running the build in the resulting image.
-   [JENKINS-39631](https://issues.jenkins-ci.org/browse/JENKINS-39631) -
    Fix error status for steps within stages.
-   [JENKINS-37781](https://issues.jenkins-ci.org/browse/JENKINS-37781) -
    Add conditional execution of individual stages via the `when`
    section.
-   [JENKINS-39394](https://issues.jenkins-ci.org/browse/JENKINS-39394) -
    Removing `notifications` completely, renaming `postBuild` to `post`
    for consistency with post-stage actions.
-   [JENKINS-39799](https://issues.jenkins-ci.org/browse/JENKINS-39799) -
    Fix a bug with invalid `post` contents.

## Version 0.5
(Nov 2, 2016)

-   [JENKINS-37823](https://issues.jenkins-ci.org/browse/JENKINS-37823) -
    `wrappers` section for wrapping the entire build in a block-scoped
    step, like `retry` or `timeout`.
-   [JENKINS-38433](https://issues.jenkins-ci.org/browse/JENKINS-38433) -
    `agent` backends are now pluggable.
-   [JENKINS-39245](https://issues.jenkins-ci.org/browse/JENKINS-39245) -
    Added `environment` section support in stages.
-   [JENKINS-39244](https://issues.jenkins-ci.org/browse/JENKINS-39244) -
    Added `tools` section support in stages.
-   [JENKINS-38993](https://issues.jenkins-ci.org/browse/JENKINS-38993) -
    Deterministic order for post-build/stage condition execution.
-   [JENKINS-39011](https://issues.jenkins-ci.org/browse/JENKINS-39011) -
    Properly error out if the `pipeline` step is present but not at the
    top-level.
-   [JENKINS-39109](https://issues.jenkins-ci.org/browse/JENKINS-39109) -
    Add a configuration option for what label to use for docker agents.
-   [JENKINS-38865](https://issues.jenkins-ci.org/browse/JENKINS-38865) -
    Split the AST into a separate plugin so others can depend on it
    without pulling everything in.
-   [JENKINS-38331](https://issues.jenkins-ci.org/browse/JENKINS-38331) -
    Per-stage configuration for agent.
-   [JENKINS-37792](https://issues.jenkins-ci.org/browse/JENKINS-37792) -
    Post-stage actions added.

## Version 0.4
(Oct 11, 2016)

-   0.3 was inadvertently built with Java 8 - so a new release is
    needed.
-   [JENKINS-37824](https://issues.jenkins-ci.org/browse/JENKINS-37824) -
    Support for job properties, triggers and build parameters.

## Version 0.3
(Oct 10, 2016)

-   [JENKINS-38818](https://issues.jenkins-ci.org/browse/JENKINS-38818) -
    Correctly escape string constants when generating groovy from AST
-   [JENKINS-38564](https://issues.jenkins-ci.org/browse/JENKINS-38564) -
    API to convert json step blob to step syntax (and back) - one step
    at a time
-   [JENKINS-37788](https://issues.jenkins-ci.org/browse/JENKINS-37788) -
    Use `isLiteral` instead of `isConstant`
-   [JENKINS-38426](https://issues.jenkins-ci.org/browse/JENKINS-38426) -
    Allow non-literal expressions for environment variable values.
-   [JENKINS-38242](https://issues.jenkins-ci.org/browse/JENKINS-38242) -
    Allow specifying arguments for Docker.
-   [JENKINS-38152](https://issues.jenkins-ci.org/browse/JENKINS-38152) -
    Expose the execution model on the `WorkflowRun`.
-   [JENKINS-37932](https://issues.jenkins-ci.org/browse/JENKINS-37932) -
    Add `agent any` to replace `agent label:""`.
-   [JENKINS-38097](https://issues.jenkins-ci.org/browse/JENKINS-38097) -
    Execute empty named stages for any planned stages after a stage
    fails so that execution model and actual execution match up.

## Version 0.2
(Sept 8, 2016)

-   [JENKINS-37897](https://issues.jenkins-ci.org/browse/JENKINS-37897) -
    switch to block-scoped stages and add synthetic stages for
    notifications and postBuild.
-   [JENKINS-37828](https://issues.jenkins-ci.org/browse/JENKINS-37828) -
    Properly reject mixes of `parallel` and other steps.
-   [JENKINS-37928](https://issues.jenkins-ci.org/browse/JENKINS-37928) -
    Properly detect sections without values.
-   [JENKINS-38047](https://issues.jenkins-ci.org/browse/JENKINS-38047) -
    Allow multiple unnamed parameters in declarative subset.

## Version 0.1
(Aug 30, 2016)

-   Initial beta release. Functional but limited.


