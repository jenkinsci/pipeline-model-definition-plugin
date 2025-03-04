/*
 See the documentation for more options:
 https://github.com/jenkins-infra/pipeline-library/
*/
buildPlugin(
  timeout: 120, // reduce test failures due to timeout
  useContainerAgent: true, // Set to `false` if you need to use Docker for containerized tests
  configurations: [
    [platform: 'linux', jdk: 21],
    // TODO https://github.com/jenkins-infra/helpdesk/issues/4574 [platform: 'windows', jdk: 17],
])
