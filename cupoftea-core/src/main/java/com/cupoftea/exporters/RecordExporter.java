package com.cupoftea.exporters;

import java.util.List;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.interfaces.IRecordExporter;

public class RecordExporter implements IRecordExporter {

    private final DtoExporter delegate;

    public RecordExporter(CupOfTea cupOfTea) {
        this.delegate = new DtoExporter(cupOfTea);
    }

    @Override
    public <T extends Record> List<T> exportToRecords(Class<T> recordClass) {
        if (recordClass == null) {
            throw new ExportException("Target Record class cannot be null");
        }
        if (!recordClass.isRecord()) {
            throw new ExportException("Class " + recordClass.getName() + " is not a Java Record");
        }
        return this.delegate.exportToDtos(recordClass);
    }
}
