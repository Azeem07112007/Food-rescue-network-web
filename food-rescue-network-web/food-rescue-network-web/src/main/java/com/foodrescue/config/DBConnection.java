package com.foodrescue.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages database connectivity using standard JDBC APIs.
 * Resolves credentials and URL in the following precedence order:
 * 1. System Environment Variables (e.g. DB_URL, DB_USER, DB_PASSWORD)
 * 2. Local .env file in the workspace root
 * 3. application.properties from classpath resources
 */
public class DBConnection {

    private static final Logger LOGGER = Logger.getLogger(DBConnection.class.getName());

    private static String dbUrl;
    private static String dbUser;
    private static String dbPassword;
    private static String dbDriver = "com.mysql.cj.jdbc.Driver";
    private static boolean initialized = false;

    // Static initialization block
    static {
        loadConfiguration();
    }

    private DBConnection() {
        // Static singleton manager
    }

    /**
     * Loads database configuration with fallback hierarchy.
     */
    public static synchronized void loadConfiguration() {
        Properties props = new Properties();

        // 1. Load from classpath application.properties if available
        try (InputStream in = DBConnection.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not load application.properties from classpath: " + e.getMessage());
        }

        // 2. Load from local .env file if available
        File envFile = new File(".env");
        if (envFile.exists() && envFile.isFile()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int equalsIdx = line.indexOf('=');
                    if (equalsIdx > 0) {
                        String key = line.substring(0, equalsIdx).trim();
                        String val = line.substring(equalsIdx + 1).trim();
                        // Strip quotes if any
                        if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                            val = val.substring(1, val.length() - 1);
                        }
                        props.setProperty(key, val);
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not read .env file: " + e.getMessage());
            }
        }

        // 3. System Environment variables override
        dbUrl = resolveConfig(props, "DB_URL", "db.url",
                "jdbc:mysql://localhost:3306/food_rescue?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        dbUser = resolveConfig(props, "DB_USER", "db.user", "root");
        dbPassword = resolveConfig(props, "DB_PASSWORD", "db.password", "");
        
        String customDriver = resolveConfig(props, "DB_DRIVER", "db.driver", null);
        if (customDriver != null && !customDriver.isEmpty()) {
            dbDriver = customDriver;
        }

        // Register JDBC driver
        try {
            Class.forName(dbDriver);
            initialized = true;
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "JDBC Driver class not found: " + dbDriver, e);
        }
    }

    private static String resolveConfig(Properties props, String envKey, String propKey, String defaultValue) {
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.trim().isEmpty()) {
            return envVal.trim();
        }
        if (props.containsKey(envKey)) {
            return props.getProperty(envKey).trim();
        }
        if (props.containsKey(propKey)) {
            return props.getProperty(propKey).trim();
        }
        return defaultValue;
    }

    /**
     * Obtains a new database connection from the driver manager.
     */
    public static Connection getConnection() throws SQLException {
        if (!initialized) {
            loadConfiguration();
        }
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    /**
     * Tests connectivity to the database and returns a human-readable status message.
     */
    public static ConnectionTestResult testConnection() {
        try (Connection conn = getConnection()) {
            if (conn.isValid(3)) {
                return new ConnectionTestResult(true, "Successfully connected to MySQL database: " + conn.getCatalog());
            } else {
                return new ConnectionTestResult(false, "Connection check timed out.");
            }
        } catch (SQLException e) {
            return new ConnectionTestResult(false, "Database connection failed (" + e.getErrorCode() + "): " + e.getMessage());
        }
    }

    /**
     * Programmatically override configuration (primarily used in automated tests).
     */
    public static synchronized void setConfiguration(String url, String user, String password, String driver) {
        dbUrl = url;
        dbUser = user;
        dbPassword = password;
        if (driver != null) {
            dbDriver = driver;
        }
        try {
            Class.forName(dbDriver);
            initialized = true;
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "Could not load custom JDBC driver: " + dbDriver, e);
        }
    }

    public static String getDbUrl() {
        return dbUrl;
    }

    public static String getDbUser() {
        return dbUser;
    }

    public static class ConnectionTestResult {
        private final boolean success;
        private final String message;

        public ConnectionTestResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}
