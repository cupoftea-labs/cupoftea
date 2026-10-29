package com.cupoftea.sql;

import java.sql.Connection;
import javax.sql.DataSource;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.SqlExporter;
import com.cupoftea.sql.configurations.SqlExporterConfiguration;
import com.cupoftea.spi.FormatWriter;
import com.cupoftea.spi.WriteOptions;
import com.cupoftea.table.interfaces.IDataFrame;

public class SqlFormatWriter implements FormatWriter {

    @Override
    public String format() {
        return "sql";
    }

    @Override
    public void write(IDataFrame dataFrame, WriteOptions options) {
        if (options == null) {
            throw new ExportException("WriteOptions cannot be null");
        }

        CupOfTea cot = new CupOfTea(dataFrame);
        SqlExporter exporter = new SqlExporter(cot);
        SqlExporterConfiguration config = options.getOption("configuration", new SqlExporterConfiguration());

        String table = (String) options.getOption("table");
        if (table == null || table.trim().isEmpty()) {
            throw new ExportException("SQL export requires 'table' option to be specified");
        }

        Object connObj = options.getOption("connection");
        if (connObj instanceof Connection) {
            exporter.exportToTable((Connection) connObj, table, config);
            return;
        }

        Object dsObj = options.getOption("dataSource");
        if (dsObj instanceof DataSource) {
            exporter.exportToTable((DataSource) dsObj, table, config);
            return;
        }

        throw new ExportException("Invalid WriteOptions for SQL format. Must provide 'connection' or 'dataSource' along with 'table'.");
    }
}
