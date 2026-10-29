package com.cupoftea.sql;

import java.sql.Connection;
import java.sql.ResultSet;
import javax.sql.DataSource;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.SqlImporter;
import com.cupoftea.sql.configurations.SqlImporterConfiguration;
import com.cupoftea.spi.FormatReader;
import com.cupoftea.spi.ReadOptions;
import com.cupoftea.table.interfaces.IDataFrame;

public class SqlFormatReader implements FormatReader {

    @Override
    public String format() {
        return "sql";
    }

    @Override
    public IDataFrame read(ReadOptions options) {
        if (options == null) {
            throw new ImportException("ReadOptions cannot be null");
        }

        CupOfTea target = options.getTargetDataFrame() != null
                ? new CupOfTea(options.getTargetDataFrame())
                : new CupOfTea();

        SqlImporter importer = new SqlImporter(target);
        SqlImporterConfiguration config = options.getOption("configuration", new SqlImporterConfiguration());

        Object rsObj = options.getOption("resultSet");
        if (rsObj instanceof ResultSet) {
            importer.importFromResultSet((ResultSet) rsObj, config);
            return target.getDataFrame();
        }

        String query = (String) options.getOption("query");
        String table = (String) options.getOption("table");

        Object connObj = options.getOption("connection");
        if (connObj instanceof Connection) {
            Connection conn = (Connection) connObj;
            if (query != null) {
                importer.importFromQuery(conn, query, config);
                return target.getDataFrame();
            } else if (table != null) {
                importer.importFromTable(conn, table, config);
                return target.getDataFrame();
            }
        }

        Object dsObj = options.getOption("dataSource");
        if (dsObj instanceof DataSource) {
            DataSource ds = (DataSource) dsObj;
            if (query != null) {
                importer.importFromQuery(ds, query, config);
                return target.getDataFrame();
            } else if (table != null) {
                importer.importFromTable(ds, table, config);
                return target.getDataFrame();
            }
        }

        throw new ImportException("Invalid ReadOptions for SQL format. Must provide 'resultSet', or 'connection'/'dataSource' with 'query' or 'table'.");
    }
}
