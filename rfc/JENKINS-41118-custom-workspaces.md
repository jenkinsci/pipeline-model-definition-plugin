## RFC: [JENKINS-41118](https://issues.jenkins-ci.org/browse/JENKINS-41118) - custom workspaces in Declarative

* Author: Andrew Bayer
* Created Date: February 23, 2017
* Approved Date: March 2, 2017
* Target Release: 1.1
* JIRA: [JENKINS-41118](https://issues.jenkins-ci.org/browse/JENKINS-41118)
* Status: Approved

### Problem Statement

As described in the ticket, there are multiple reasons why one 
may want to use a hardcoded workspace directory under
`$JENKINS_HOME/workspace` for a Pipeline, such as reusing the
same checkout of a very large repository, or guaranteeing a
certain full directory layout to make Golang happy, etc. This
may be needed both at the top level and per-stage, so that
individual stages could have a custom workspace when they had
their own agent configuration as well.

### Runtime Implementation

The actual runtime implementation is very clear - if a custom
workspace is specified, then immediately after entering the
relevant `node` block and before doing a `checkout scm`, we
would enter a `ws('some-dir')` block. 

### Syntax

#### Option Name

Regardless of where the option ends up living in the syntax, I
propose that it be named `customWorkspace`. This maps to the
Freestyle "Custom Workspace" option, which is analogous to the
behavior of the `ws(...)` step (though not exactly, since
"Custom Workspace" is an absolute path while `ws(...)` is
relative to the workspace root on the agent).

#### Where the Option is Specified

The easiest place to add this option so that it would be
available for use anywhere an `agent` could be specified would
be in the `agent` itself, as an additional option on relevant
`DeclarativeAgent` types. That would currently mean `label`,
`docker`, and `dockerfile` - `any` is a special alias for
`label ''` and can't be modified, and this would not be
relevant for `none`. 

For `docker` and `dockerfile`, adding an additional option is 
trivial, given that they already have additional options for 
`args` etc. But for `label`, this would be the first additional 
option and so would create the first case of specifying `label` 
with a block. However, the block form of `label` actually works 
currently - `label 'something'` is in fact just a shortcut for 
`label { label 'something' }` behind the scenes.

In order to address the ugliness of `label { label 'something'; customWorkspace 'some/path' }`,
we will add an additional `@Symbol` to the `label` agent type,
`node`. It'll be fully compatible with `label` - either can be
used interchangeably. But the JSON and Groovy parsers will 
automatically switch from `label` to `node` if it's used with
multiple options, i.e., in the block form. This will result in 
it not mattering at all what symbol the editor is using, because
it will serialize out as `node` when it's got a block no matter
what.

#### Alternatives

Perhaps the best alternative to `agent` configuration would be 
to have the custom workspace specified as a top-level and 
per-stage option. This would require adding an `options` 
section for `stage`, since we don't have anywhere for options 
to be configured per-stage currently. 

#### Examples

```groovy
agent {
    node {
        label "some-label" // This could be changed to something other than "label"
        customWorkspace "some/path"
    }
}

agent {
    docker {
        image "some-image"
        args "-v /tmp:/tmp -p 80:80"
        customWorkspace "some/path"
    }
}
```
