## RFC: [JENKINS-41334](https://issues.jenkins-ci.org/browse/JENKINS-41334) - Parallel stage execution

* Author: Andrew Bayer
* Created Date: February 27, 2017
* Approved Date: n/a
* JIRA: [JENKINS-41334](https://issues.jenkins-ci.org/browse/JENKINS-41334)
* Status: Draft

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
* Naming of `parallel` is up for debate.

### Runtime Implementation

There are two options for runtime. Which one we go with depends on
Blue Ocean visualization decisions. The first option is to simply nest
stage executions, while the second option is to not actually use the
`stage` step for the nested `stage`s and instead utilize the `stage`
names as the names of parallel branches.

These two options each have their pros and cons. A pro in one option
tends to lead to a con in the other option.

#### Pro/Con

* Stage status/metadata (i.e., skipped due to failure/`when`) would
    not work in the parallel branches approach, since that relies on
    adding metadata to the stage representation in the flow graph. This
    could cause issues in visualization.
* Nested stages would allow for identical code paths for execution of
    a stage regardless of whether the stage is a nested stage or not, so
    the code would be simpler than for parallel branches.
* Blue Ocean supports visualization of parallel branches already, so
    less work would be needed there than for nested stages.

#### Proposed Approach

Nested stages is a superior approach, even though it requires design
and implementation work in Blue Ocean for proper visualization. Given
that the nested stages will still be executed in named parallel
branches, Blue Ocean as it is now will continue to visualize nested
stages as if they were just parallel branches, albeit without the
stage status metadata.

Given the benefits to the nested stages over parallel branches in
terms of implementation and metadata, it seems preferrable to go with
the nested stages implementation.

### Visualization

Note that this section is at least partially speculative. We will
defer to the Blue Ocean team for the final decision on visualization
of parallel stages. We would recommend visualizing the parallel stages
in the same manner that parallel branches are visualized in Blue Ocean
currently, but using the `stage` name, status and metadata rather than
the parallel branch.
