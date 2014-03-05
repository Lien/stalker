/*
 * Copyright (C) 2013 Vandal LLC
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

package com.vandalsoftware.tools.gradle

import com.vandalsoftware.tools.classfile.ClassCollector
import com.vandalsoftware.tools.classfile.ClassInfo
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * @author Jonathan Le
 */
class Usages extends DefaultTask {
    public static final char CLASS_SEPARATOR_CHAR = '.' as char
    Set<File> files
    Set<String> classNames

    @TaskAction
    void usages() {
        Set srcRoots = srcRoots() as Set
        Set srcClassPaths = classpaths() as Set
        def inputs = input() as Set
        // Keep track of unique classes being examined
        Set inputClasses = new LinkedHashSet(inputs)
        LinkedList classesToExamine = new LinkedList(inputs)
        Set inputClassNames = new LinkedHashSet()
        final ClassCollector sourceReader = new ClassCollector();
        srcClassPaths.each() { File dir ->
            sourceReader.collect(dir);
        }
        while (!classesToExamine.isEmpty()) {
            String filePath = classesToExamine.remove()
            logger.info "Examining $filePath"
            srcRoots.each() { File srcRoot ->
                if (filePath.startsWith(srcRoot.path)) {
                    String relFilePath = filePath.substring(srcRoot.path.length() + 1,
                            filePath.indexOf(".java")) + ".class"
                    srcClassPaths.each() { File cp ->
                        File f = new File(cp, relFilePath)
                        ClassInfo info = sourceReader.collectFile(f)
                        if (info != null) {
                            String cname = info.thisClassName;
                            logger.info "$cname is an affected class"
                            inputClassNames.add(cname);
                            collectInputs(sourceReader.findSubclasses(cname),
                                    srcRoot, inputClasses, classesToExamine,
                                    { logger.info "-> $it extends $cname" })
                            if (info.isInterface()) {
                                collectInputs(sourceReader.findImplementations(cname),
                                        srcRoot, inputClasses, classesToExamine,
                                        { logger.info "-> $it implements $cname" })
                            }
                        }
                    }
                }
            }
        }
        final ClassCollector targetReader = new ClassCollector();
        def targetClassPaths = targets()
        targetClassPaths.each() { File dir ->
            targetReader.collect(dir);
        }
        // Check each file for usage of each input
        File[] used = targetReader.findUsages(inputClassNames);
        classNames = new LinkedHashSet<>()
        used.each() { f ->
            targetClassPaths.each() { File target ->
                if (f.path.startsWith(target.path)) {
                    logger.info "Usage detected: $f"
                    classNames.add(pathToClassName(target.path, f.path, ".class"))
                }
            }
        }
        if (used.length == 0) {
            logger.lifecycle "No usages detected."
        }
    }

    private static Collection<String> collectInputs(Collection<String> classes, File srcRoot,
                                                    inputClasses, classesToExamine, log) {
        classes.each() { className ->
            log(className)
            def path = classNameToPath(className, srcRoot.path, '.java')
            if (!inputClasses.contains(path)) {
                inputClasses.add(path)
                classesToExamine.add(path)
            }
        }
    }

    private static String classNameToPath(String className, String basePath, String extension) {
        return new File(basePath, className.replace(CLASS_SEPARATOR_CHAR,
                File.separatorChar) + extension);
    }

    boolean checkInputs() {
        return classpaths() && srcRoots() && input() && checkTargets()
    }

    private boolean checkTargets() {
        boolean run = false
        targets().each() { File dir ->
            if (dir.exists()) {
                run = true
            }
        }
        return run
    }

    private static String pathToClassName(String basePath, String path, String extension) {
        return path.substring(basePath.length() + 1,
                path.indexOf(extension)).replace(File.separatorChar, CLASS_SEPARATOR_CHAR)
    }
}
