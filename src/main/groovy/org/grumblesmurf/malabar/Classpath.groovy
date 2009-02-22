/**
 * Copyright (c) 2009 Espen Wiborg <espenhw@grumblesmurf.org>
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */ 
package org.grumblesmurf.malabar

import org.codehaus.groovy.tools.RootLoader;
import org.objectweb.asm.*;
import java.lang.reflect.*;
import java.util.jar.*;

class Classpath 
{
    def bootUrls = [];

    def extUrls = [];
    
    def urls = [];
    
    Classpath() {
        System.getProperty("sun.boot.class.path").split(File.pathSeparator).each() {
            bootUrls << "file:" + it
        }

        System.getProperty("java.ext.dirs").split(File.pathSeparator).each() { dir ->
            try {
                (dir as File).eachFileMatch(~/.*.jar/) {
                    extUrls << "file:" + it
                }
            } catch (FileNotFoundException e) {
                // EAT IT
            }
        }
    }

    Classpath(entries) {
        this()
        entries.each{
            if ((it as File).isDirectory() && !it.endsWith("/"))
              it = it + "/"
            this.urls << "file:" + it
        }
    }

    def asClassPath() {
        return urls.collect{it.substring(5)}.join(':')
    }
    
    private classloader;

    def classMap = [:]
    
    def getClasses(String unqualifiedName) {
        if (classMap.isEmpty()) {
            def classnamecollector = { fileName, path ->
                def classbinaryname = fileName[0..-7];
                def simplename = classbinaryname[classbinaryname.lastIndexOf('$') + 1..-1]
                def pkgname = path.replace('/', '.')
                                
                if (!classMap.containsKey(simplename)) {
                    classMap[simplename] = []
                }
                classMap[simplename] << pkgname + "." + classbinaryname
            }
            
            def classcollector = { 
                URI uri = new URI(it);
                File file = new File(uri);
                String absolutePath = file.absolutePath
                if (file.exists()) {
                    if (file.isFile()) {
                        new JarFile(file).entries().each{ entry ->
                            if (entry.name.endsWith(".class")) {
                                def entryname = entry.name
                                def filename = entryname[entryname.lastIndexOf('/') + 1..-1]
                                def path = ""
                                if (entryname.lastIndexOf('/') > -1) {
                                    path = entryname.substring(0, entryname.lastIndexOf('/'))
                                }
                                classnamecollector(filename, path)
                            }
                        }
                    } else {
                        file.eachFileRecurse{ classFile ->
                            if (classFile.name.endsWith(".class")) {
                                classnamecollector(classFile.name,
                                                   classFile.parent[absolutePath.length() + 1 .. -1])
                            }
                        }
                    }
                }
            }
            
            bootUrls.each{
                classcollector(it)
            }
            extUrls.each{
                classcollector(it)
            }
            urls.each {
                classcollector(it)
            }
        }
        Utils.printAsLispList classMap[unqualifiedName];
    }

    def getClassLoader() {
        if (classloader == null) {
            URL[] realUrls = new URL[urls.size()];
            urls.eachWithIndex() { it, i ->
                realUrls[i] = new URL(it)
            }
            classloader = new RootLoader(realUrls,
                                         ClassLoader.systemClassLoader.parent)
        }
        return classloader
    }

    def getClassReader(Class c) {
        return getClassReader(c.getName())
    }

    def getClassReader(String className) {
        InputStream input =
            this.classLoader.getResourceAsStream(className.replace('.', '/') + ".class")
        if (input == null) {
            throw new RuntimeException("Couldn't find class file for " + className)
        }
        
        return new ClassReader(input);
    }

    def getMembers(String className) {
        Class c = this.classLoader.loadClass(className)
        print "("
        getMembersInternal(c, [] as Set)
        println ")"
    }
    
    def getMembersInternal(Class c, Set seenMethods) {
        ClassReader cr = getClassReader(c.name);
        ClassInfo cir = new ClassInfo();
        cr.accept(cir, false);

        def quotify = {
            return "\"${it}\""
        }
        
        def printName = {
            print " :name " + quotify(it.name)
        }

        def printModifiers = {
            print " :modifiers (" + Modifier.toString(it.modifiers) + ")"
        }

        def printDeclaringClass = {
            print " :declaring-class " + quotify(it.declaringClass.name)
        }

        def printArguments = { it, names ->
            print " :arguments"
            print " ("
            it.genericParameterTypes.eachWithIndex{ pt, i ->
                print "(:type " + quotify(typeString(pt))
                if (names && names[++i]) {
                    printName(names[i])
                }
                print ") "
            }
            print ") " 
        }

        def printExceptions = {
            if (it.genericExceptionTypes.length > 0) {
                print " :throws (";
                it.genericExceptionTypes.each{ ex ->
                    print " " + quotify(typeString(ex))
                }
                print ") "
            }
        }
        
        def methodPrinter = {
            def desc = it.name + Type.getMethodDescriptor(it)
            if (seenMethods.contains(desc))
                return

            seenMethods << desc
            
            def methodParameters = cir.methodParameters[desc];

            print "(method"
            printName(it)
            printModifiers(it)
            print " :returntype " + quotify(typeString(it.genericReturnType))
            printArguments(it, methodParameters)
            printDeclaringClass(it)
            printExceptions(it)
            println ")"
        }

        def constructorPrinter = {
            def desc = "<init>" + methodDescriptor(it)
            if (seenMethods.contains(desc))
                return

            seenMethods << desc
            def methodParameters = cir.methodParameters[desc];
            
            print "(constructor"
            printModifiers(it)
            printArguments(it.genericParameterTypes)
            printExceptions(it)
            printDeclaringClass(it)
            println ")"
        }

        def fieldPrinter = {
            print "(field "
            printName(it)
            print " :type " + quotify(typeString(it.genericType))
            printModifiers(it)
            printDeclaringClass(it)
            println ")"
        }

        def classPrinter = {
            print "(class :name " + quotify(it.simpleName)
            printModifiers(it)
            printDeclaringClass(it)
            println ")"
        }
        
        c.declaredFields.each{
            if (!Modifier.isPrivate(it.modifiers))
                fieldPrinter(it)
        }

        c.declaredClasses.each{
            if (!Modifier.isPrivate(it.modifiers))
                classPrinter(it)
        }
        
        c.declaredConstructors.each{
            if (!Modifier.isPrivate(it.modifiers))
                constructorPrinter(it)
        }
        
        c.declaredMethods.each{
            if (!Modifier.isPrivate(it.modifiers))
                methodPrinter(it)
        }
        
        if (c.isInterface()) {
            c.interfaces.each{
                getMembersInternal(it, seenMethods)
            }
        }
        if (c.superclass != null) {
            getMembersInternal(c.superclass, seenMethods)
        }
    }

    def typeString(type) {
        if (type instanceof Class) {
            if (type.enclosingClass) {
                return typeString(type.enclosingClass) + "." + type.simpleName
            }
            return type.simpleName
        }
        if (type instanceof GenericArrayType) {
            return typeString(type.genericComponentType) + "[]"
        }
        if (type instanceof ParameterizedType) {
            def str = typeString(type.rawType)
            str += "<";
            type.actualTypeArguments.eachWithIndex{ it, i ->
                if (i > 0) {
                    str += ", "
                }
                str += typeString(it)
            }
            str += ">"
            return str;
        }
        
        return type.toString()
    }

    def methodDescriptor(Constructor cons) {
        def str = "(";
        cons.parameterTypes.eachWithIndex{ it, i ->
            if (!it.isArray() && !it.isPrimitive()) {
                str += "L";
            }
            if (!it.isPrimitive()) {
                str += Type.getInternalName(it)
            } else {
                if (it == Boolean.TYPE) {
                    str += "Z"
                } else if (it == Long.TYPE) {
                    str += "J"
                } else {
                    str += it.name.toUpperCase().charAt(0)
                }
            }
            if (!it.isArray() && !it.isPrimitive()) {
                str += ";"
            }
        }
        str += ")V"
        return str
    }
}