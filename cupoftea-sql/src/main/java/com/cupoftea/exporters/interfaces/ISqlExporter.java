package com.cupoftea.exporters.interfaces;

import java.sql.Connection;
import javax.sql.DataSource;

import com.cupoftea.sql.configurations.SqlExporterConfiguration;

public interface ISqlExporter {

    void exportToTable(Connection conn, String tableName);

    void exportToTable(Connection conn, String tableName, SqlExporterConfiguration config);

    void exportToTable(DataSource dataSource, String tableName);

    void exportToTable(DataSource dataSource, String tableName, SqlExporterConfiguration config);
}
