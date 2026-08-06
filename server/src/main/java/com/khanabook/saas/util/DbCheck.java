package com.khanabook.saas.util;

import java.sql.*;

public class DbCheck {
    public static void main(String[] args) throws Exception {
        String url = System.getenv("DB_URL");
        if (url == null) url = "jdbc:postgresql://localhost:5432/kbook_saas";
        String user = System.getenv("DB_USERNAME");
        if (user == null) user = "postgres";
        String pass = System.getenv("DB_PASSWORD");
        if (pass == null) pass = "root";
        url = url.replaceAll("localhost", "127.0.0.1");
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            DatabaseMetaData meta = conn.getMetaData();
            // List schemas
            System.out.println("=== Schemas ===");
            ResultSet schemas = meta.getSchemas();
            while (schemas.next()) {
                System.out.println("  Schema: " + schemas.getString("TABLE_SCHEM"));
            }
            // List all tables
            System.out.println("\n=== All Tables ===");
            ResultSet tables = meta.getTables(null, "public", "%", new String[]{"TABLE"});
            boolean found = false;
            while (tables.next()) {
                System.out.println("  " + tables.getString("TABLE_SCHEM") + "." + tables.getString("TABLE_NAME"));
                found = true;
            }
            if (!found) {
                // Try all schemas
                tables = meta.getTables(null, null, "%", new String[]{"TABLE"});
                while (tables.next()) {
                    System.out.println("  " + tables.getString("TABLE_SCHEM") + "." + tables.getString("TABLE_NAME"));
                }
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
