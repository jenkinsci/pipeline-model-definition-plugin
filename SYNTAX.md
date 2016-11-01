# Pipeline Model Definition Syntax spec

## pipeline
* *Description*: Top level step, containing a closure with the model configuration inside it.
* *Parameters*: none
* *Takes a Closure*: Yes.
* *Closure Contents*: See below.

## Within `pipeline`
These are sections that are specified directly within the `pipeline` argument closure.

### agent
* *Description*: Specifies where the build or stage will run. 
* *Required*: Yes for the top-level `pipeline` closure, optional for individual `stage` closures.
* *Allowed In*: Top-level `pipeline` closure and individual `stage` closures.
* *Parameters*: Either a `Map` of one or more arguments or one of two constants - `none` or `any`.
    * *Map Keys*:
        * Note that this will be an `ExtensionPoint`, so plugins will be able to add to the available image providers.
        * `docker`
            * *Type*: `String`
            * *Description*: If given, uses this Docker image for the container the build will run in. If no `label` is 
            given, the container will be run within a simple `node { ... }` block.
            * *Example*: `docker:'ubuntu'`
        * `label` 
            * *Type*: `String`
            * *Description*: If given, uses this label for the node the build will run on - if `docker` is also 
            specified, the container will run on that node.
            * *Example*: `label:'hi-speed'`
    * `none`
        * *Type*: bareword
        * *Description*: If given, node/image management will need to be specified explicitly in stages and no 
        automatic `checkout scm` call will occur.
* *Takes a Closure*: No
* *Examples*:
    * `agent label:'has-docker', docker:'ubuntu:lts'`
    * `agent docker:'ubuntu:lts'`
    * `agent label:'hi-speed'`
    * `agent none`
    * `agent any`

### environment
* *Description*: A sequence of `key = value` pairs, which will be passed to the `withEnv` step the build will be 
executed within.
* *Required*: No
* *Allowed In*: Top-level `pipeline` or `stage` closures only.
* *Parameters*: None
* *Takes a Closure*: Yes
* *Closure Contents*: One or more lines with `foo = 'bar'` variable name/value pairs.
    * The name doesn't need to be quoted.
* *Examples*:

```
environment {
   CXX              = 'g++-4.8'
   SAUCE_USERNAME   = 'angular-ci'
   SAUCE_ACCESS_KEY = '9b988f434ff8-fbca-8aa4-4ae3-35442987'
   someVar          = 'someValue'
}
```

### stages
* *Description*: A sequence of one or more Pipeline `stage`s, each of which consists of a sequence of steps.
* *Required*: Yes
* *Allowed In*: Top-level `pipeline` closure only.
* *Parameters*: None
* *Takes a Closure*: Yes
* *Closure Contents*: one or more `stage` blocks, as described below.

#### stage
* *Description*: A block for a single `stage`, containing a sequence of steps. Note that this syntax matches up with the
to-be-released block-scoped `stage` syntax in base Pipeline.
* *Required*: At least one is required.
* *Parameters*: A single `String`, the name for the `stage`.
* *Takes a Closure*: Yes
* *Closure Contents*: A `steps` block containing one or more Pipeline steps, including block-scoped steps and the 
special `script` block described below, and optionally, certain configuration sections that allow being set on a 
per-stage basis.
    * *NOTE*: Currently only the `agent` section can be configured per-stage.
    * *NOTE*: Only the "declarative subset" of Groovy is allowed by default. See below for details on that subset.
    * *NOTE*: The `parallel` step is a special case - it can only be used if it's the sole step in the `stage`.
* *Examples*:

```
stages {
    stage('foo') {
        steps {
            echo 'bar'
        }
    }
}

stages {
    stage('first') {
        steps {
            timeout(time:5, unit:'MINUTES') {
                sh "mvn clean install -DskipTests"
            }
        }
    }
        
    stage('second') {
        agent label:'some-node'
        steps {
            checkout scm
            sh "mvn clean install"
        }
    }
}

stages {
    stage('parallel-stage') {
        steps {
            parallel(
                firstBlock: {
                    echo "First block of the parallel"
                },
                secondBlock: {
                    echo "Second block of the parallel"
                }
            )
        }
    }
}
```

#### script
* *Description*: A block within a `stage`'s steps that can contain Pipeline code not subject to the "declarative" subset
described below.
* *Required*: No
* *Parameters*: None
* *Takes a Closure*: Yes
* *Closure Contents*: Any valid Pipeline code.
* *Examples*:

```
image docker:'java:7'
stages {
    stage 'build' {
        sh 'mvn install'
        script {
            // any valid Pipeline Script goes here
            ['ie','chrome'].each { sh "./test.sh ${it}" }
        }
    }
}
```

### tools
* *Description*: A section defining tools to auto-install and put on the PATH. This is ignored if `image none`
is specified.
* *Required*: No
* *Allowed In*: Top-level `pipeline`  or `stage` closures only.
* *Parameters*: None
* *Takes a Closure*: Yes
* *Closure Contents*: Names and versions of tools configured in Jenkins to install. 
    * Tool names are aliases to the `ToolDescriptor` class for that tool, and must be one of a list of pre-configured 
    possible tools. Currently that's hardwired to just `maven`, `java`, and `gradle`, but this will be changed to be an 
    extensible system. The tool (and its version) must already be configured on the Jenkins master in use.
    * Tool versions are the names for specific tool installations configured in Jenkins.
* *Examples*:

```
tools {
    maven "apache-maven-3.0.1"
    java "JDK 1.8"
}
```

### notifications
* *Description*: Defines notifications to be sent after build completion, assuming build status conditions are met.
* *Required*: No
* *Allowed In*: Top-level `pipeline` closure only.
* *Parameters*: None
* *Takes a Closure*: Yes
* *Closure Contents*: A sequence of one or more build conditions containing Pipeline steps to run. See below for 
definitions of build conditions and their contents.

### postBuild
* *Description*: Defines post-build actions to be run after build completion, assuming build status conditions are met.
Note that `postBuild` steps are run *before* `notifications`.
* *Required*: No
* *Allowed In*: Top-level `pipeline` closure only.
* *Parameters*: None
* *Takes a Closure*: Yes
* *Closure Contents*: A sequence of one or more build conditions containing Pipeline steps to run. See below for 
definition of build conditions and their contents.

### Build Conditions
* *Description*: Closures named for a particular build condition, containing Pipeline steps to run if that condition is
met.
* *Required*: One or more required in either `notifications` or `postBuild` if they exist.
* *Parameters*: None
* *Possible Condition Names*:
    * Currently hardcoded, but will be changed to be extensible.
    * `always`: Run regardless of build status.
    * `aborted`: Run if build is aborted - note that this may not actually work.
    * `success`: Run if the build is successful (or more accurately, if the build result hasn't been set to anything else).
    * `unstable`: Run if the build result is unstable.
    * `failure`: Run if the build failed.
    * `changed`: Run if the build's result is different from the previous build's result.
* *Takes a Closure*: Yes
* *Closure Contents*: A sequence of Pipeline steps, such as could be included in a `stage`. Runs in an unspecified 
`node { ... }` block if `image none` was specified.
* *Examples*:

```
notifications {
    always {
        email recipient: ['one@example.com','two@example.com'], subject: "Build complete", body: "Your build has completed"
    }
}
    
postBuild {
    failure {
        sh "bash ./cleanup-from-failure.sh"
    }
    success {
        sh "git push origin master"
    }
}
```

### Triggers
* *Description*: Triggers for this job, as used in other Jenkins jobs.
* *Required*: No
* *Allowed In*: Top-level `pipeline` closure only.
* *Parameters*: None
* *Takes a Closure*: Yes
* *Closure Contents*: A sequence of one or more trigger configurations, using `@Symbol` names for constructors.
    * Note that `[$class: 'Foo', arg1: 'something', ...]` syntax can not be used, only `cron('@daily')` and the like.
    * Also note that the `SCMTrigger` won't work with the `scm` `@Symbol` - with Jenkins 2.22 or later, the `pollScm` symbol does work.
* *Examples*:

```
triggers {
    cron('@daily')
}
```

### Build Parameters
* *Description*: Build parameters that will be prompted for at build time.
* *Required*: No
* *Allowed In*: Top-level `pipeline` closure only.
* *Parameters*: None
* *Takes a Closure*: Yes
* *Closure Contents*: A sequence of one or more parameter definition configurations, using `@Symbol` names for constructors.
    * Note that `[$class: 'Foo', arg1: 'something', ...]` syntax can not be used, only `booleanParam(...)` and the like.
* *Examples*:

```
parameters {
    booleanParam(defaultValue: true, description: '', name: 'flag')
    string(defaultValue: '', description: '', name: 'SOME_STRING')
}
```

### Job Properties
* *Description*: Other job properties, such as build discarding, limiting concurrent builds, and more.
* *Required*: No
* *Allowed In*: Top-level `pipeline` closure only.
* *Parameters*: None
* *Takes a Closure*: Yes
* *Closure Contents*: A sequence of one or more job property configurations, using `@Symbol` names for constructors.
    * Note that `[$class: 'Foo', arg1: 'something', ...]` syntax can not be used, only `booleanParam(...)` and the like.
    * Note that the `parameters` and `pipelineTriggers` `@Symbol`s cannot be used here directly.
* *Examples*:

```
jobProperties {
    buildDiscarder(logRotator(numToKeepStr:'1'))
    disableConcurrentBuilds()
}
```

## Declarative Subset of Groovy
* Top-level has to be a block
* No semicolons as statement separators. Each statement has to be on its own line
* Block must only consists of method call statements, assignment statements, or property reference statement
    * A property reference statement is treated as no-arg method invocation. So for example, `input` is treated as `input()`
* Expression has to be one of the following:
    * Literals (except class literals)
    * Numbers: `1`, `3`
    * Booleans: `true`, `false`
    * String literals regardless of their quotations: `"foo"`, `'bar'`
    * Multi-line string literals
    * Variable references: `x`
    * Sequence of property references: `x.y.z`
    * GString: `"hello ${exp}"`
    * Literal list: `[exp,exp,...]`
    * Literal map where keys are all constants: `[a:exp, b:exp, ... ]`
    * Method calls where the left hand side is a variable reference or a sequence of property references: `x.y.z(...)`
    * Method calls (including `@Symbol` constructors like used above in job properties, triggers and build parameters) where there is no left hand side.
    * Closure without parameters: `{ ... }`
