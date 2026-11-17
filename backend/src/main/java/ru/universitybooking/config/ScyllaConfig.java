package ru.universitybooking.config;

import com.datastax.oss.driver.api.core.CqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;

@Configuration
public class ScyllaConfig {

    private static final Logger log = LoggerFactory.getLogger(ScyllaConfig.class);

    @Value("${scylla.contact-points}")
    private String contactPoints;

    @Value("${scylla.local-datacenter}")
    private String localDatacenter;

    @Value("${scylla.keyspace}")
    private String keyspace;

    @Bean(destroyMethod = "close")
    public CqlSession cqlSession() {
        log.info("Подключение к ScyllaDB: {} (datacenter={})", contactPoints, localDatacenter);

        String host;
        int port;
        int colon = contactPoints.lastIndexOf(':');
        if (colon > 0) {
            host = contactPoints.substring(0, colon);
            port = Integer.parseInt(contactPoints.substring(colon + 1));
        } else {
            host = contactPoints;
            port = 9042;
        }

        CqlSession session = CqlSession.builder()
                .addContactPoint(InetSocketAddress.createUnresolved(host, port))
                .withLocalDatacenter(localDatacenter)
                .build();

        initSchema(session);
        return session;
    }

    private void initSchema(CqlSession session) {
        log.info("Инициализация схемы ScyllaDB (keyspace={})", keyspace);
        session.execute(
                "CREATE KEYSPACE IF NOT EXISTS " + keyspace +
                " WITH replication = {'class': 'NetworkTopologyStrategy', '" + localDatacenter + "': 3}" +
                " AND durable_writes = true"
        );

        session.execute(
                "CREATE TABLE IF NOT EXISTS " + keyspace + ".events_by_result_day (" +
                "    result      text," +
                "    day_utc     text," +
                "    occurred_at timestamp," +
                "    event_id    uuid," +
                "    operation   text," +
                "    booking_id  bigint," +
                "    error_code  text," +
                "    PRIMARY KEY ((result, day_utc), occurred_at, event_id)" +
                ") WITH CLUSTERING ORDER BY (occurred_at DESC, event_id DESC)"
        );

        session.execute(
                "CREATE TABLE IF NOT EXISTS " + keyspace + ".events_by_operation_day (" +
                "    operation   text," +
                "    day_utc     text," +
                "    occurred_at timestamp," +
                "    event_id    uuid," +
                "    result      text," +
                "    booking_id  bigint," +
                "    error_code  text," +
                "    PRIMARY KEY ((operation, day_utc), occurred_at, event_id)" +
                ") WITH CLUSTERING ORDER BY (occurred_at DESC, event_id DESC)"
        );

        session.execute(
                "CREATE TABLE IF NOT EXISTS " + keyspace + ".events_by_booking_day (" +
                "    booking_id  bigint," +
                "    day_utc     text," +
                "    occurred_at timestamp," +
                "    event_id    uuid," +
                "    operation   text," +
                "    result      text," +
                "    error_code  text," +
                "    PRIMARY KEY ((booking_id, day_utc), occurred_at, event_id)" +
                ") WITH CLUSTERING ORDER BY (occurred_at DESC, event_id DESC)"
        );

        log.info("Схема ScyllaDB готова");
    }
}
