/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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
 *
 */


pipeline {
    agent {
        label "here"
    }
    stages {
        stage("One") {
            steps {
                echo "Hello"
            }
        }
        stage("IsChange") {
            when {
                buildingChange()
            }
            steps {
                echo "World"
            }
        }
        stage("ChangeId") {
            when {
                buildingChange id: "1?"
            }
            steps {
                echo "Id is in the tens"
            }
        }
        stage("ChangeAndBranch") {
            when {
                buildingChange id: "1?", branch: "CR-1?"
            }
            steps {
                echo "We are in the tens"
            }
        }
        stage("ChangeTarget") {
            when {
                buildingChange target: "release-*"
            }
            steps {
                echo "Target release"
            }
        }
        stage("ChangeBranch") {
            when {
                buildingChange branch: "CR-*"
            }
            steps {
                echo "From CR branch"
            }
        }
        stage("ChangeFork") {
            when {
                buildingChange fork: "fork"
            }
            steps {
                echo "From origin fork"
            }
        }
        stage("ChangeUrlX") {
            when {
                buildingChange urlX: ".*example.*"
            }
            steps {
                echo "From urlX example"
            }
        }
        stage("ChangeTitle") {
            when {
                buildingChange title: "*regression*"
            }
            steps {
                echo "title names a regression"
            }
        }
        stage("ChangeTitleX") {
            when {
                buildingChange titleX: ".*regression.*"
            }
            steps {
                echo "titleX names a regression"
            }
        }
        stage("ChangeAuthor") {
            when {
                buildingChange author: "Bob*"
            }
            steps {
                echo "Author is a cool guy"
            }
        }
        stage("ChangeAuthorX") {
            when {
                buildingChange authorX: "bob.*"
            }
            steps {
                echo "AuthorX is nice"
            }
        }
        stage("ChangeAuthorDisplayName") {
            when {
                buildingChange authorDisplayName: "Bob*"
            }
            steps {
                echo "Author displays coolness"
            }
        }
        stage("ChangeAuthorDisplayNameX") {
            when {
                buildingChange authorDisplayNameX: "Bob.*"
            }
            steps {
                echo "AuthorX displays coolness"
            }
        }
        stage("ChangeAuthorEmail") {
            when {
                buildingChange authorEmail: "*@example.com"
            }
            steps {
                echo "Author has a cool job"
            }
        }
        stage("ChangeAuthorEmailX") {
            when {
                buildingChange authorEmailX: "[a-z\\.]+@example.com"
            }
            steps {
                echo "Author is probably a robot"
            }
        }
    }
}