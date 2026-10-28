package com.cupoftea.importers.interfaces;

import java.util.Collection;

public interface IRecordImporter {

    <T extends Record> void importFromRecords(Collection<T> records);

}
