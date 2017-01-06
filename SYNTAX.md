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
* *Parameters*: Either a `Closure` of one key and either a single value or a `Closure` of multiple configuration pairs, or one of two constants - `none` or `any`.
    * *Map Keys*:
        * Note that this will be an `ExtensionPoint`, so plugins will be able to add to the available image providers.
        * `docker`
            * *Description*: If given, uses this Docker image for the container the build will run in. If no `nodeLabel` is 
            given, the container will be run within a simple `node { ... }` block.
        * `dockerfile`
            * *Description*: If given, builds from the Dockerfile in the repository and runs in a container using the resulting image.
        * `label` 
            * *Description*: If given, uses this label for the node the build will run on - if `docker` is also 
            specified, the container will run on that node.
    * `none`
        * *Type*: bareword
        * *Description*: If given, node/image management will need to be specified explicitly in stages and no 
        automatic `checkout scm` call will occur.
    * `any`
        * *Type*: bareword
        * *Description*: If given, any available agent will be used.
* *Takes a Closure*: yes
* *Examples*:

```groovy
agent {
    label 'hi-speed'
}

agent {
    docker 'ubuntu:lts'
}
 
agent {
    docker {
        image 'ubuntu:lts'
        nodeLabel 'has-docker'
        args "-v /tmp:/tmp -p 80:80"
    }
}

agent none

agent any
```

### environment
* *Description*: A sequence of `key = value` pairs, which will be passed to the `withEnv` step the build will be 
executed within.
* *Required*: No
* *Allowed In*: Top-level `pipeline` or `stage` closures only.
* *Parameters*: None
* *Takes a Closure*: Yes
* *Closure Contents*: One or more lines with `foo = 'bar'` variable name/value pairs.
    * The name doesn't need to be quoted.
    * Bind credentials using the `credentials('<id>')` function.
      *NOTE*: credentials binding requires that the pipeline or stage is running within an agent.
      In the example below the credentials gets bound in slightly different ways depending of the type of the key;
        * If it is a `Secret Text` the variable `SAUCE_ACCESS` will contain that text.
        * If it is a `Secret File` the variable `SAUCE_ACCESS` will contain a path to the file on the build agent.
        * If it is a `Standard username and password` credential, three variables will be added to the environment:
            * `SAUCE_ACCESS` containing `<username>:<password>`
            * `SAUCE_ACCESS_USR` containing the username
            * `SAUCE_ACCESS_PSW` containing the password
* *Examples*:

```groovy
environment {
   CXX          = 'g++-4.8'
   SAUCE_ACCESS = credentials('sauce-lab-dev')
   someVar      = 'someValue'
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
* *Closure Contents*: 
    * A `steps` block containing one or more Pipeline steps, including block-scoped steps and the special `script` block described below, and optionally, certain configuration sections that allow being set on a per-stage basis.
        * *NOTE*: Only the "declarative subset" of Groovy is allowed by default. See below for details on that subset.
        * *NOTE*: The `parallel` step is a special case - it can only be used if it's the sole step in the `stage`.
    * An `agent` section can be configured per-stage, see above.
    * An optional `when` block specifying if the stage should run or not.
      It can contain arbitrary Groovy code, but needs to return `true` if the stage should run or `false` if not.
    * An optional `post` block that runs after the steps in the stage. See `post` below.  
* *Examples*:

```groovy
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
        agent {
            label 'some-node'
        }
        when {
            env.BRANCH == 'master'
        }
        steps {
            checkout scm
            sh "mvn clean install"
        }
        post {
            always {
                email recipient: ['one@example.com','two@example.com'], subject: "Master Build complete", body: "Your build has completed"
            }
            failure {
                sh "bash ./cleanup-from-failure.sh"
            }
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

```groovy
image docker:'java:7'
stages {
    stage 'build' {
        steps {
            sh 'mvn install'
            script {
                // any valid Pipeline Script goes here
                def browsers = ["ie", "chrome", "safari"]
                for (int i = 0; i < browsers.size(); i++) {
                    def browser = browsers.get(i)
                    sh "./test.sh ${browser}"
                }
            }
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

```groovy
tools {
    maven "apache-maven-3.0.1"
    java "JDK 1.8"
}
```

### post
* *Description*: Defines post-build actions to be run after build completion, assuming build status conditions are met.
* *Required*: No
* *Allowed In*: Top-level `pipeline` closure only.
* *Parameters*: None
* *Takes a Closure*: Yes
* *Closure Contents*: A sequence of one or more build conditions containing Pipeline steps to run. See below for 
definition of build conditions and their contents.

### Build Conditions
* *Description*: Closures named for a particular build condition, containing Pipeline steps to run if that condition is
met.
* *Required*: One or more required in `post` if it exists.
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

```groovy
post {
    always {
        email recipient: ['one@example.com','two@example.com'], subject: "Build complete", body: "Your build has completed"
    }
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

```groovy
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

```groovy
parameters {
    booleanParam(defaultValue: true, description: '', name: 'flag')
    string(defaultValue: '', description: '', name: 'SOME_STRING')
}
```

### Options
* *Description*: Traditional `JobProperty`s, such as `buildDiscarder` or `disableConcurrentBuilds`, Declarative-specifc options, such as `skipDefaultCheckout`, and "wrappers" that should wrap the entire build, such as `timeout`.
* *Required*: No
* *Allowed In*: Top-level `pipeline` closure only.
* *Parameters*: None
* *Takes a Closure*: Yes
* *Closure Contents*: A sequence of one or more Declarative option or job property configurations, using `@Symbol` names for constructors.
    * Note that `[$class: 'Foo', arg1: 'something', ...]` syntax can not be used, only `booleanParam(...)` and the like.
    * Note that the `parameters` and `pipelineTriggers` `@Symbol`s cannot be used here directly.
* *Examples*:

```groovy
options {
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
    * Method calls (including `@Symbol` constructors like used above in options, triggers and build parameters) where there is no left hand side.
    * Closure without parameters: `{ ... }`
