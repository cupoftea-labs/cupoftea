package com.cupoftea.exporters.interfaces;

import java.util.List;

public interface IRecordExporter {

    <T extends Record> List<T> exportToRecords(Class<T> recordClass);

}
