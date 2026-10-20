package com.CupOfTea.utils.interfaces;

import com.CupOfTea.utils.configurations.PrinterConfiguration;

public interface IPrinter {

    public void print();

    default public void print(PrinterConfiguration configuration) {
        print();
    }

    default public String format() {
        return "";
    }

    default public String format(PrinterConfiguration configuration) {
        return format();
    }

}
