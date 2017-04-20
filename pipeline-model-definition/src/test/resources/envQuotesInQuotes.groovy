/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

pipeline {
    agent any

    environment {
        // Regrettably needed since GitSampleRepoRule doesn't set BRANCH_NAME.
        BRANCH_NAME = "master"

        GRADLE_OPTIONS = "--no-daemon --rerun-tasks -PBUILD_NUMBER=${env.BUILD_NUMBER} -PBRANCH=\"${env.BRANCH_NAME}\""

        MULTILINE_SINGLE = '''
Look at me 'here'
'''

        MULTILINE_DOUBLE = """
The branch name is "${env.BRANCH_NAME}"
"""

    }

    stages {
        stage("foo") {
            steps {
                echo "GRADLE_OPTIONS is ${env.GRADLE_OPTIONS}"
                echo "MULTILINE_SINGLE is ${env.MULTILINE_SINGLE}"
                echo "MULTILINE_DOUBLE is ${env.MULTILINE_DOUBLE}"
            }
        }
    }
}



