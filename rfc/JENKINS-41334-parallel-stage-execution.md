## RFC: [JENKINS-41334](https://issues.jenkins-ci.org/browse/JENKINS-41334) - Parallel stage execution

* Author: Andrew Bayer
* Created Date: February 27, 2017
* Approved Date: March 21, 2017
* Revised Date: April 13, 2017
* Target Release: 1.2
* JIRA: [JENKINS-41334](https://issues.jenkins-ci.org/browse/JENKINS-41334)
* Status: Approved

### Problem Statement

The `parallel` step does not fit smoothly into Declarative. It doesn't
allow for parallel stage execution, it doesn't utilize the `agent`
definition, etc. There needs to be the ability to run `stage`s in
parallel, with the full power of the Declarative syntax enabled -
i.e., `agent`, `post`, `when`, etc.

### Syntax

#### Proposed example

```groovy
stage('foo') {
    parallel {
        stage('first') {
            steps {
                echo "First branch"
            }
        }
        stage('second') {
            steps {
                echo "Second branch"
            }
        }
    }
}
```

#### Details

* A `stage` has to contain one and only one of `steps` or
    `parallel`. They cannot be combined in the same `stage`.
* `stage` configuration can be done on both a `stage` containing
    `parallel`, or `stage`s within `parallel`, i.e., `agent`, `when`,
    `post`, etc.
* `agent`, `environment`, and `tools` specified on the "parent"
    `stage` will apply to child `stage`s in the same way that top-level
    configuration applies to `stage`s currently.
* Arbitrarily deep nesting of parallel `stage`s would not be
    allowed. Only `stage`s that themselves are not within a `parallel`
    would be able to contain further `parallel` `stage`s.
* `stage`s within a `parallel` would not allow use of the `parallel`
    step either, so as to prevent visualization confusion.

### Runtime Implementation

Based on input from the Blue Ocean team regarding the two possible runtime
implementations (actually nesting stages within the `parallel` branches vs 
simply utilizing the `parallel` branches), we have decided to just use the
`parallel` branches themselves. This should hopefully require little to no
changes in Blue Ocean visualization or Bismuth in order to work, meaning only
changes to the editor will be needed outside of Declarative itself.

Each `parallel` branch will be given the `stage` name specified, with `agent`,
`environment`, etc configured before running the `steps` for the `stage` inside
the `parallel` branch, followed by any `post` actions. This will require some
changes in Declarative's runtime logic to properly attach the supplementary 
metadata we attach to `stage`s (i.e., "Failed but continued", "Skipped due to
earlier failure", etc) to branches, but that won't be difficult. Everything 
else will just function properly right out of the box.

### Visualization

No changes should be needed in Blue Ocean visualization, since we will simply
be using `parallel` branches with no nested `StageStep` executions.

#### Revisions
* Apr 13, 2017 - switched name in the syntax to `parallel` to prevent 
confusion with the existing `parallel` step, switched target release to 1.2.