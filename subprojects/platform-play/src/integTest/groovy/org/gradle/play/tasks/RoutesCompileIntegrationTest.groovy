/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */



package org.gradle.play.tasks
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

class RoutesCompileIntegrationTest extends AbstractIntegrationSpec {
    def destinationDirPath = "build/routes/"
    def destinationDir = file(destinationDirPath)

    def setup(){
        buildFile << """
        repositories{
            ivy {
                url "http://repo.typesafe.com/typesafe/releases/"
                layout "maven"
            }
        }

        configurations{
            playRoutes
        }

        dependencies{
            playRoutes "com.typesafe.play:routes-compiler_2.10:2.3.5"
            playRoutes "org.scala-lang:scala-library:2.10.4"
            playRoutes "commons-io:commons-io:2.0.1"
        }

        task routesCompile(type:RoutesCompile) {
            compilerClasspath = configurations.playRoutes
            routesCompilerVersion = "2.3.5"
            outputDirectory = file('build/routes')
        }
"""
    }

    def "can run RoutesCompile"(){
        given:
        withRoutesTemplate()
        expect:
        succeeds("routesCompile")
        and:
        destinationDir.assertHasDescendants("controllers/routes.java", "routes_reverseRouting.scala", "routes_routing.scala")
    }

    def "runs compiler incrementally"(){
        when:
        withRoutesTemplate()
        then:
        succeeds("routesCompile")
        and:
        destinationDir.assertHasDescendants("controllers/routes.java", "routes_reverseRouting.scala", "routes_routing.scala")

        withRoutesTemplate("foo")
        and:
        succeeds("routesCompile")
        then:
        destinationDir.assertHasDescendants("controllers/routes.java", "routes_reverseRouting.scala", "routes_routing.scala",
                "controllers/foo/routes.java", "foo/routes_reverseRouting.scala", "foo/routes_routing.scala")

        when:
        file("conf/foo.routes").delete()
        then:
        succeeds("routesCompile")
        and:
        destinationDir.assertHasDescendants("controllers/routes.java", "routes_reverseRouting.scala", "routes_routing.scala")
    }

    def "removes stale output files in incremental compile"(){
        given:
        TestFile templateFile = withRoutesTemplate()
        succeeds("routesCompile")

        and:
        destinationDir.assertHasDescendants("controllers/routes.java", "routes_reverseRouting.scala", "routes_routing.scala")
        def routesFirstCompileSnapshot = file(destinationDirPath, "controllers/routes.java").snapshot();
        def revRoutingFirstCompileSnapshot = file(destinationDirPath, "routes_reverseRouting.scala").snapshot();
        def routingFirstCompileSnapshot = file(destinationDirPath, "routes_routing.scala").snapshot();

        when:
        templateFile.delete()

        then:
        succeeds("routesCompile")
        and:
        file(destinationDirPath, "controllers/routes.java").assertHasNotChangedSince(routesFirstCompileSnapshot);
        file(destinationDirPath, "routes_reverseRouting.scala").assertHasNotChangedSince(revRoutingFirstCompileSnapshot);
        file(destinationDirPath, "routes_routing.scala").assertHasNotChangedSince(routingFirstCompileSnapshot);
    }

    def withRoutesTemplate(String packageName = "") {
        def routesFile = packageName.isEmpty() ? file("conf", "routes") : file("conf", packageName + ".routes")
        def packageId = packageName.isEmpty() ? "" : ".$packageName"
        routesFile.createFile()
        routesFile << """
# Routes
# This file defines all application routes (Higher priority routes first)
# ~~~~

# Home page
GET     /                          controllers${packageId}.Application.index()

# Map static resources from the /public folder to the /assets URL path
GET     /assets/*file               controllers.Assets.at(path="/public", file)
"""
        buildFile << """
routesCompile.source '${routesFile.toURI()}'
"""

        return routesFile
    }
}
