package com.CupOfTea.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ClassFinder {

    public List<Class<?>> findClasses(String packageName) throws Exception {

        String path = packageName.replace('.', '/');
        File directory = new File("target/classes/" + path);

        List<Class<?>> classes = new ArrayList<>();
        if (directory.exists()) {
            for (String file : directory.list()) {
                if (file.endsWith(".class")) {
                    try {
                        String className = packageName + '.' + file.substring(0, file.length() - 6);
                        classes.add(Class.forName(className));
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return classes;

    }

}
