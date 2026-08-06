package com.khanabook.saas;

import java.sql.*;

/**
 * Quick utility to check the EasebuzzSubMerchant and RestaurantProfile
 * tables. Run with: mvn exec:java -Dexec.mainClass="com.khanabook.saas.QuickDbCheck"
 */
public class QuickDbCheck {
    public static void main(String[] args) throws Exception {
        String url = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/kbook_saas");
        String user = System.getenv().getOrDefault("DB_USERNAME", "postgres");
        String pass = System.getenv().getOrDefault("DB_PASSWORD", "root");

        System.out.println("Connecting to: " + url + " as " + user);

        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            // Check users
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                     "SELECT id, email, phone_number, whatsapp_number, restaurant_id FROM users " +
                     "WHERE email LIKE '%nandha%' OR phone_number LIKE '%2536%' LIMIT 5")) {
                System.out.println("\n=== Users ===");
                while (rs.next()) {
                    System.out.printf("  id=%d, email=%s, phone=%s, whatsapp=%s, restaurant_id=%d%n",
                        rs.getLong("id"), rs.getString("email"),
                        rs.getString("phone_number"), rs.getString("whatsapp_number"),
                        rs.getLong("restaurant_id"));
                }
            }

            // Check restaurant profiles with easebuzz enabled
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                     "SELECT id, restaurant_id, shop_name, easebuzz_enabled FROM restaurant_profile " +
                     "ORDER BY id DESC LIMIT 20")) {
                System.out.println("\n=== Restaurant Profiles ===");
                while (rs.next()) {
                    System.out.printf("  id=%d, restaurant_id=%d, shop_name=%s, easebuzz_enabled=%s%n",
                        rs.getLong("id"), rs.getLong("restaurant_id"),
                        rs.getString("shop_name"), rs.getBoolean("easebuzz_enabled"));
                }
            }

            // Check EasebuzzSubMerchant
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                     "SELECT id, restaurant_id, sub_merchant_id, status, kyc_status, business_name " +
                     "FROM easebuzz_sub_merchant ORDER BY id DESC LIMIT 20")) {
                System.out.println("\n=== Easebuzz Sub-Merchants ===");
                while (rs.next()) {
                    System.out.printf("  id=%d, restaurant_id=%d, sub_merchant_id=%s, status=%s, kyc=%s, name=%s%n",
                        rs.getLong("id"), rs.getLong("restaurant_id"),
                        rs.getString("sub_merchant_id"), rs.getString("status"),
                        rs.getString("kyc_status"), rs.getString("business_name"));
                }
            }
        }
    }
}
