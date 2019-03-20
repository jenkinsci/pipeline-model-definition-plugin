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
    environment {
        SSH_CRED = credentials("sshCred")
    }

    agent any

    stages {
        stage("foo") {
            steps {
                echo "SSH_CRED_USR is $SSH_CRED_USR"
                echo "SSH_CRED is $SSH_CRED"
                writeFile file: "sshCredUsr.txt", text: "${SSH_CRED_USR}"
                writeFile file: "sshCredKey.txt", text: "${readFile file: "$SSH_CRED"}"
                archive "**/*.txt"
            }
        }
        stage("bar") {
            environment {
                CRED_NAME = "sshWithPassCred"
                SSH_WITH_PASS_CRED = credentials("${CRED_NAME}")
            }
            steps {
                echo "SSH_WITH_PASS_CRED_USR is $SSH_WITH_PASS_CRED_USR"
                echo "SSH_WITH_PASS_CRED_PSW is $SSH_WITH_PASS_CRED_PSW"
                echo "SSH_WITH_PASS_CRED is $SSH_WITH_PASS_CRED"
                writeFile file: "sshWithPassCredUsrPass.txt", text: "${SSH_WITH_PASS_CRED_USR}:${SSH_WITH_PASS_CRED_PSW}"
                writeFile file: "sshWithPassCredKey.txt", text: "${readFile file: "$SSH_WITH_PASS_CRED"}"
                archive "**/*.txt"
            }
        }
    }
}