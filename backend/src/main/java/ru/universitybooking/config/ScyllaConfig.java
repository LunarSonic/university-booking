package ru.universitybooking.config;

import com.datastax.oss.driver.api.core.CqlSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

@Configuration
public class ScyllaConfig {

    @Value("${scylla.contact-points}")
    private String contactPoints;

    @Value("${scylla.local-datacenter}")
    private String localDatacenter;

    @Value("${scylla.keyspace}")
    private String keyspace;

    @Bean(destroyMethod = "close")
    public CqlSession cqlSession() throws IOException {
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

    private void initSchema(CqlSession session) throws IOException {
        ClassPathResource resource = new ClassPathResource("scylla-schema.cql");
        String cql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        cql = cql.replace("datacenter1", localDatacenter).replace("university_events", keyspace);

        for (String stmt : cql.split(";")) {
            String trimmed = stmt.strip();
            if (!trimmed.isEmpty()) {
                session.execute(trimmed);
            }
        }
    }
}
