/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package dk.statsbiblioteket.summa.storage.database.h2;

import dk.statsbiblioteket.summa.common.Record;
import dk.statsbiblioteket.summa.common.configuration.Configurable;
import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.storage.StorageUtils;
import dk.statsbiblioteket.summa.storage.api.QueryOptions;
import dk.statsbiblioteket.summa.storage.database.DatabaseStorage;
import dk.statsbiblioteket.summa.storage.database.MiniConnectionPoolManager;
import dk.statsbiblioteket.summa.storage.database.MiniConnectionPoolManager.StatementHandle;
import dk.statsbiblioteket.util.Files;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.h2.jdbcx.JdbcDataSource;

/**
 * Storage implementation on top of the H" database engine.
 */
public class H2Storage extends DatabaseStorage implements Configurable {
    /** Private logger. */
    private static Log log = LogFactory.getLog(H2Storage.class);
    /** DB file. */
    private static final String DB_FILE = "summa_h2storage";
    //public static final int META_LIMIT = 50*1024*1024; // MAX_VALUE instead?

    /**
     * We optimize the index statistics after this many flushes.
     */
    public static final int OPTIMIZE_INDEX_THRESHOLD = 100000;

    /** The DB username. */
    private String username;
    /** The DB password. */
    private String password;
    /** The DB location. */
    private File location;
    /** The max allowed connection to the DB. */
    private int maxConnections;
    /** True if a new database should be created. */
    private boolean createNew = true;
    /** True if creation of database should be forced. */
    private boolean forceNew = false;
    /** True if we should us L2 cache. */
    private boolean useL2cache = true;

    /** The database source. */
    private JdbcDataSource dataSource;
    /** The connection pool. */
    private MiniConnectionPoolManager pool;
    /** Number of flushes. */
    private long numFlushes;

    /**
     * Boolean property determining whether the second level page cache should
     * be enabled in the H2 database. The L2 cache provides some performance
     * gains on larger bases, but might be a slow down on smaller bases.
     * <p/>
     * Enable this feature with care since some memory leaks has been
     * experienced when this feature is enabled.
     * <p/>
     * Default is {@code false}.
     */
    public static final String CONF_L2CACHE = "summa.storage.database.l2cache";
    /** Default usage for H2 of L2 cached is false. */
    public static final boolean DEFAULT_L2CACHE = false;

    /**
     * Creates a H2 database storage, given the configuration.
     * @param conf The configuration.
     * @throws IOException If error occur while doing IO work for creation of
     * database.
     */
    public H2Storage(Configuration conf) throws IOException {
        super(conf);
        log.trace("Constructing H2Storage");
        numFlushes = 0;
        username = conf.getString(CONF_USERNAME, "");
        password = conf.getString(CONF_PASSWORD, "");
        maxConnections = conf.getInt(CONF_MAX_CONNECTIONS,
                                     DEFAULT_MAX_CONNECTIONS);
        useL2cache = conf.getBoolean(CONF_L2CACHE, DEFAULT_L2CACHE);

        // Database file location
        if (conf.valueExists(CONF_LOCATION)) {
            log.debug("Property '" + CONF_LOCATION + "' exists, using value '"
                      + conf.getString(CONF_LOCATION) + "' as location");
            location = new File(conf.getString(CONF_LOCATION));
        } else {
            location = new File (StorageUtils.getGlobalPersistentDir(conf),
                                 "storage" + File.separator + "h2");
            log.debug("Using default location '" + location + "'");
        }

        if (!location.equals(location.getAbsoluteFile())) {
            log.debug(String.format("Transforming relative location '%s' to "
                                    + "absolute location'%s'",
                                    location, location.getAbsoluteFile()));
            location = location.getAbsoluteFile();
        }

        if (location.isFile()) {
            throw new ConfigurationException("Database path contains a regular"
                                             + " file");
        }

        // Create new DB?
        if (conf.valueExists(CONF_CREATENEW)) {
                createNew = conf.getBoolean(CONF_CREATENEW);
        }

        // Force new DB?
        if (conf.valueExists(CONF_FORCENEW)) {
                forceNew = conf.getBoolean(CONF_FORCENEW);
        }

        log.debug("H2Storage extracted properties username: " + username
                 + ", password: "
                  + (password == null ? "[undefined]" : "[defined]")
                  + ", location: '" + location + "', createNew: " + createNew
                  + ", forceNew: " + forceNew);
        init(conf);
        log.trace("Construction completed");
    }

    /**
     * Closes H2 Storage, thereby closes storage and database connections.
     * @throws IOException if throws while closing storage.
     */
    @Override
    public void close() throws IOException {
        super.close();
        try {
            pool.dispose();
        } catch (SQLException e) {
            final String error = "SQLException while closing pooled connection";
            log.warn(error);
            throw new IOException(error, e);
        }
        log.info("H2 Storage closed.");        
    }

    @Override
    protected void connectToDatabase(Configuration configuration) throws
                                                               IOException {
        log.info("Establishing connection to H2 with  username '" + username
                 + "', password " + (password == null || "".equals(password) ?
                                            "[undefined]" : "[defined]")
                 + ", location '" + location + "', createNew " + createNew
                 + " and forceNew " + forceNew);

        if (new File(location, DB_FILE+".h2.db").isFile() ||
            new File(location, DB_FILE+".data.db").isFile()) {
            /* Database location exists*/
            log.debug("Old database found at '" + location + "'");
            if (forceNew) {
                log.info("Deleting existing database at '" + location + "'");
                try {
                    Files.delete(location);
                } catch (IOException e) {
                    throw new RemoteException("Could not delete old database "
                                              + "at '" + location + "'", e);
                }
            } else {
                log.info("Reusing old database at '" + location + "'");
                createNew = false;
            }
        } else {
            log.debug("No database at '" + location + "'");
            if (createNew) {
                log.info("Creating new database at '" + location + "'");
            } else {
                throw new IOException("No database exists at '" + location
                                          + " and createNew is false. The "
                                          + "metadata storage cannot run "
                                          + "without a backend. Exiting");
            }
        }

        location.mkdirs();
        if (!location.isDirectory()) {
            throw new IOException("Database location '" + location
                                  + "' not a directory");
        }

        dataSource = new JdbcDataSource();

        // Speedup for large dbs, but slowdown for smaller ones
        String l2cache = "";
        if (useL2cache) {
            log.debug("Enabling H2 L2 cache");
            l2cache = ";CACHE_TYPE=SOFT_LRU";
        }

        dataSource.setURL("jdbc:h2:"+location.getAbsolutePath()
                          + File.separator + DB_FILE + l2cache);

        if (username != null) {
            dataSource.setUser(username);
        }

        if (password != null) {
            dataSource.setPassword(password);
        }

        pool = new MiniConnectionPoolManager(dataSource, maxConnections);

        log.info("Connected to database at '" + location + "'");

        if (createNew) {
            log.info("Creating new table for '" + location + "'");
            createSchema();
        }
        setMaxMemoryRows();
    }

    /**
     * Optimizes the tables.
     */
    private void optimizeTables() {
        Connection conn = getConnection();
        try {
            // Rebuild the table selectivity indexes used by the query optimizer
            log.info("Optimizing table selectivity");
            Statement stmt = conn.createStatement();
            stmt.execute("ANALYZE");
        } catch (SQLException e) {
            log.warn("Failed to optimize table selectivity", e);
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                log.warn("Failed to close connection: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public synchronized void flush(Record rec) throws IOException {
        numFlushes++;
        numFlushes = numFlushes % OPTIMIZE_INDEX_THRESHOLD;

        if (numFlushes == 0) {
            optimizeTables();
        }
        super.flush(rec);
    }

    /*
    * Note: the 'synchronized' part of this method decl is paramount to
    * allowing us to set our transaction level to
    * Connection.TRANSACTION_READ_UNCOMMITTED
    */
    @Override
    public synchronized void flushAll(List<Record> recs) throws IOException {
        if (numFlushes > OPTIMIZE_INDEX_THRESHOLD) {
            numFlushes = 0;
            optimizeTables();
        }
        numFlushes += recs.size();
        super.flushAll(recs);
    }

    /**
     * The purpose of this method is to make sure that H2's limit on the
     * maximum number of memory buffered rows is bigger than the pageSize
     * of the result sets
     */
    private void setMaxMemoryRows() {
        Connection conn = getConnection();
        try {
            // There might be several rows per record if the records has
            // relations. There will be one extra row per relation
            int maxMemoryRows = getPageSize()*3;
            log.debug("Setting MAX_MEMORY_ROWS to " + maxMemoryRows);
            Statement stmt = conn.createStatement();
            stmt.execute("SET MAX_MEMORY_ROWS " + maxMemoryRows);
        } catch (SQLException e) {
            log.warn("Failed to set MAX_MEMORY_ROWS this may affect performance"
                      + " on large result sets");
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                log.warn("Failed to close connection: " + e.getMessage(), e);
            }
        }
    }

    /**
     * We override this method because H2 only uses its custom
     * JdbcSQLExceptions, and not the proper exceptions according to the
     * JDBC api
     * @param e sql exception to inspect
     * @return whether or not {@code e} represents an integrity constraint
     *         violation
     */
    @Override
    protected boolean isIntegrityConstraintViolation (SQLException e) {
        // The H2 error codes for integrity constraint violations are
        // numbers between 23000 and 23999
        return (e instanceof SQLIntegrityConstraintViolationException ||
                (e.getErrorCode() >= 23000 && e.getErrorCode() < 24000));
    }

    @Override
    protected Connection getConnection() {
        return pool.getConnection();
    }

    @Override
    protected StatementHandle prepareStatement(String sql) {
        return pool.prepareStatement(sql);
    }

    @Override
    protected PreparedStatement getManagedStatement(StatementHandle handle)
                                                           throws SQLException {
        return pool.getManagedStatement(handle);
    }

    @Override
    protected String getMetaColumnDataDeclaration() {
        return " BYTEA";
    }

    @Override
    protected String getDataColumnDataDeclaration() {
        return " BYTEA";
    }

    /**
     * The method {@link DatabaseStorage#touchParents} does the touching
     * in one single SQL call. This call involves a nested <code>SELECT</code>
     * which triggers a performance issue on large databases.
     * <p/>
     * Because of this we override the generic method with one that does manual
     * looping over all parents. It should still perform fairly good.
     *
     * @param id The record id of the record which parents to update.
     * @param options Any query options that may affect how the touching should
     *                be carried out.
     * @param conn The database connection.
     * @throws IOException In case of communication errors with the database.
     * @throws SQLException If error occur while executing the SQL.
     */
    @Override
    protected void touchParents(String id,
                                QueryOptions options, Connection conn)
                                              throws IOException, SQLException {
        touchParents(id, null, options, conn);
    }

    /**
     * @see #touchParents(String, QueryOptions, Connection);
     * @param id The record id of the record which parents to update.
     * @param options Any query options that may affect how the touching should
     *                be carried out.
     * @param conn The database connection.
     * @param touched The set of already touched IDs.
     * @throws IOException In case of communication errors with the database.
     * @throws SQLException If error occur while executing the SQL.
     */
    private void touchParents(String id, Set<String> touched,
                              QueryOptions options, Connection conn)
                                              throws IOException, SQLException {
        if (log.isTraceEnabled()) {
            log.trace("Preparing to touch parents of " + id);
        }
        List<Record> parents = getParents(id, options, conn);

        if (parents == null || parents.isEmpty()) {
            if (log.isTraceEnabled()) {
                log.trace("No parents to update for record " + id);
            }
            return;
        }

        touched = touched != null ? touched : new HashSet<String>(10);

        // Touch each parent and recurse upwards
        for (Record parent : parents) {
            if (touched.contains(parent.getId())) {
                log.trace("Parent '" + parent.getId() + "' already touched");
                continue;
            }
            touched.add(parent.getId());

            touchRecord(parent.getId(), conn);
            touchParents(parent.getId(), touched, options, conn);
        }
    }

    // H2 is very bad at large result sets (_very_)
    @Override
    public boolean usePagingResultSets() {
        return true;
    }

    // Generally Java embedded DBs are bad at JOINs over tables with many rows
    @Override
    public boolean useLazyRelationLookups() {
        return true;
    }

    @Override
    public String getPagingStatement(String sql) {
        return sql + " LIMIT " + getPageSize();
    }
}
