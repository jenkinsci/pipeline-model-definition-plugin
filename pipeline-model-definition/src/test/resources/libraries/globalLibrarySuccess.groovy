/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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
    agent {
        label "master"
    }
    stages {
        stage("foo") {
            steps {
                // TODO: foo.bar() global variable object calls do not work as the step, though they do work as arguments.
                //acmeVar.hello('Pipeline')
                //acmeVar.foo('seed')

                // TODO: Due to the above methods not running/working, this method will fail as a result of acmeVar.x not being set.
                //echo '['+acmeVar.bar()+']'

                // acmeVar.baz() will work since it doesn't rely on anything else - demonstrating that it will work as a step
                // argument, if not as a step itself.
                echo('[' + acmeVar.baz() + ']')

                // TODO: Unnamed parameters won't work.
                //acmeFunc(1,2)

                acmeFuncClosure1(1) {
                    echo 'running inside closure1'
                }

                acmeFuncClosure2(1, 2) {
                    echo 'running inside closure2'
                }

                // A call method taking a map, however, will work.
                acmeFuncMap(a: 1, b: 2)

                // TODO: Passing a body that isn't composed of steps does not work. Should it work?
                //acmeBody { title = 'yolo' }
            }
        }
    }
}
