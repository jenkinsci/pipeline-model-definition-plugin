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

        MICROSERVICE_NAME = "directory"
        IMAGE_NAME = "quay.io/svc/${env.MICROSERVICE_NAME}"

        IMAGE_ID = "${env.IMAGE_NAME}:${env.TAG_NAME}"
        SOMETHING_OR_OTHER = "${env.BRANCH_NAME + "_" + env.BUILD_ID}"
        TAG_NAME = "${env.SOMETHING_OR_OTHER.replaceAll("[.:/\\\\#]", '-')}"
    }

    stages {
        stage("foo") {
            steps {
                echo "MICROSERVICE_NAME is ${env.MICROSERVICE_NAME}"
                echo "IMAGE_NAME is ${env.IMAGE_NAME}"
                echo "IMAGE_ID is ${env.IMAGE_ID}"
                echo "TAG_NAME is ${env.TAG_NAME}"
            }
        }
    }
}



