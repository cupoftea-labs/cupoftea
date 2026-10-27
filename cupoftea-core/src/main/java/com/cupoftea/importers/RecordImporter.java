package com.cupoftea.importers;

import java.util.Collection;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.interfaces.IRecordImporter;

public class RecordImporter implements IRecordImporter {

    private final DtoImporter delegate;

    public RecordImporter(CupOfTea cupOfTea) {
        this.delegate = new DtoImporter(cupOfTea);
    }

    @Override
    public <T extends Record> void importFromRecords(Collection<T> records) {
        if (records == null) {
            throw new ImportException("Record collection cannot be null");
        }
        this.delegate.importFromDtos(records);
    }
}
