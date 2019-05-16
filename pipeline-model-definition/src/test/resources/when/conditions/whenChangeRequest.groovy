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
                changeRequest()
            }
            steps {
                echo "World"
            }
        }
        stage("ChangeId") {
            when {
                changeRequest id: "1?", comparator: "GLOB"
            }
            steps {
                echo "Id is in the tens"
            }
        }
        stage("ChangeAndBranch") {
            when {
                changeRequest id: "1?", branch: "CR-1?", comparator: "GLOB"
            }
            steps {
                echo "We are in the tens"
            }
        }
        stage("ChangeTarget") {
            when {
                changeRequest target: "release-*", comparator: "GLOB"
            }
            steps {
                echo "Target release"
            }
        }
        stage("ChangeBranch") {
            when {
                changeRequest branch: "CR-*", comparator: "GLOB"
            }
            steps {
                echo "From CR branch"
            }
        }
        stage("ChangeFork") {
            when {
                changeRequest fork: "fork"
            }
            steps {
                echo "From origin fork"
            }
        }
        stage("ChangeUrlX") {
            when {
                changeRequest url: ".*example.*", comparator: "REGEXP"
            }
            steps {
                echo "From urlX example"
            }
        }
        stage("ChangeTitle") {
            when {
                changeRequest title: "*regression*", comparator: "GLOB"
            }
            steps {
                echo "title names a regression"
            }
        }
        stage("ChangeTitleX") {
            when {
                changeRequest title: ".*regression.*", comparator: "REGEXP"
            }
            steps {
                echo "titleX names a regression"
            }
        }
        stage("ChangeAuthor") {
            when {
                changeRequest author: "Bob*", comparator: "GLOB"
            }
            steps {
                echo "Author is a cool guy"
            }
        }
        stage("ChangeAuthorX") {
            when {
                changeRequest author: "bob.*", comparator: "REGEXP"
            }
            steps {
                echo "AuthorX is nice"
            }
        }
        stage("ChangeAuthorDisplayName") {
            when {
                changeRequest authorDisplayName: "Bob*", comparator: "GLOB"
            }
            steps {
                echo "Author displays coolness"
            }
        }
        stage("ChangeAuthorDisplayNameX") {
            when {
                changeRequest authorDisplayName: "Bob.*", comparator: "REGEXP"
            }
            steps {
                echo "AuthorX displays coolness"
            }
        }
        stage("ChangeAuthorEmail") {
            when {
                changeRequest authorEmail: "bob@example.com" //No comparator so should default to String.equals
            }
            steps {
                echo "Author has a cool job"
            }
        }
        stage("ChangeAuthorEmailX") {
            when {
                changeRequest authorEmail: "[a-z\\.]+@example.com", comparator: "REGEXP"
            }
            steps {
                echo "Author is probably a robot"
            }
        }
    }
}