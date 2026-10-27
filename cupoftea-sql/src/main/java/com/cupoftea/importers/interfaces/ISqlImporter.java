package com.cupoftea.importers.interfaces;

import java.sql.Connection;
import java.sql.ResultSet;
import javax.sql.DataSource;

import com.cupoftea.sql.configurations.SqlImporterConfiguration;

public interface ISqlImporter {

    void importFromResultSet(ResultSet rs);

    void importFromResultSet(ResultSet rs, SqlImporterConfiguration config);

    void importFromQuery(Connection conn, String query);

    void importFromQuery(Connection conn, String query, SqlImporterConfiguration config);

    void importFromQuery(DataSource dataSource, String query);

    void importFromQuery(DataSource dataSource, String query, SqlImporterConfiguration config);

    void importFromTable(Connection conn, String tableName);

    void importFromTable(Connection conn, String tableName, SqlImporterConfiguration config);

    void importFromTable(DataSource dataSource, String tableName);

    void importFromTable(DataSource dataSource, String tableName, SqlImporterConfiguration config);
}
