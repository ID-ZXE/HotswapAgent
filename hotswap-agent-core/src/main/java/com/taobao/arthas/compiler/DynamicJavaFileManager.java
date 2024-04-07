package com.taobao.arthas.compiler;

import java.io.IOException;
import java.util.*;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

public class DynamicJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    private static final String[] superLocationNames = { StandardLocation.PLATFORM_CLASS_PATH.name(),
            /** JPMS StandardLocation.SYSTEM_MODULES **/
            "SYSTEM_MODULES" };
    private final PackageInternalsFinder finder;

    private final DynamicClassLoader classLoader;
    private final List<MemoryByteCode> byteCodes = new ArrayList<MemoryByteCode>();

    private final Set<StringSource> sourceCodes = new HashSet<>();

    public DynamicJavaFileManager(JavaFileManager fileManager, DynamicClassLoader classLoader) {
        super(fileManager);
        this.classLoader = classLoader;

        finder = new PackageInternalsFinder(classLoader);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className,
                                               JavaFileObject.Kind kind, FileObject sibling) throws IOException {

        for (MemoryByteCode byteCode : byteCodes) {
            if (byteCode.getClassName().equals(className)) {
                return byteCode;
            }
        }

        MemoryByteCode innerClass = new MemoryByteCode(className);
        byteCodes.add(innerClass);
        classLoader.registerCompiledSource(innerClass);
        return innerClass;

//        if (JavaFileObject.Kind.SOURCE.equals(kind)) {
//            // 源码
//            for (StringSource stringSource : this.sourceCodes) {
//                if (stringSource.getClassName().equals(className)) {
//                    return stringSource;
//                }
//            }
//
//            StringSource stringSource = new StringSource(className);
//            sourceCodes.add(stringSource);
//            // 这里可以存一下动态生成的源代码, 编译完成后输出到文件夹
//            // classLoader.registerCompiledSource(stringSource);
//            return stringSource;
//
//        } else {
//            // 字节码
//            for (MemoryByteCode byteCode : this.byteCodes) {
//                if (byteCode.getClassName().equals(className)) {
//                    return byteCode;
//                }
//            }
//
//            MemoryByteCode innerClass = new MemoryByteCode(className);
//            byteCodes.add(innerClass);
//            classLoader.registerCompiledSource(innerClass);
//            return innerClass;
//        }

    }

    @Override
    public ClassLoader getClassLoader(Location location) {
        return classLoader;
    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject file) {
        if (file instanceof CustomJavaFileObject) {
            return ((CustomJavaFileObject) file).binaryName();
        } else {
            /**
             * if it's not CustomJavaFileObject, then it's coming from standard file manager
             * - let it handle the file
             */
            return super.inferBinaryName(location, file);
        }
    }

    @Override
    public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds,
                                         boolean recurse) throws IOException {
        if (location instanceof StandardLocation) {
            String locationName = ((StandardLocation) location).name();
            for (String name : superLocationNames) {
                if (name.equals(locationName)) {
                    return super.list(location, packageName, kinds, recurse);
                }
            }
        }

        // merge JavaFileObjects from specified ClassLoader
        if (location == StandardLocation.CLASS_PATH && kinds.contains(JavaFileObject.Kind.CLASS)) {
            return new IterableJoin<JavaFileObject>(super.list(location, packageName, kinds, recurse),
                    finder.find(packageName));
        }

        return super.list(location, packageName, kinds, recurse);
    }

    static class IterableJoin<T> implements Iterable<T> {
        private final Iterable<T> first, next;

        public IterableJoin(Iterable<T> first, Iterable<T> next) {
            this.first = first;
            this.next = next;
        }

        @Override
        public Iterator<T> iterator() {
            return new IteratorJoin<T>(first.iterator(), next.iterator());
        }
    }

    static class IteratorJoin<T> implements Iterator<T> {
        private final Iterator<T> first, next;

        public IteratorJoin(Iterator<T> first, Iterator<T> next) {
            this.first = first;
            this.next = next;
        }

        @Override
        public boolean hasNext() {
            return first.hasNext() || next.hasNext();
        }

        @Override
        public T next() {
            if (first.hasNext()) {
                return first.next();
            }
            return next.next();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove");
        }
    }
}
