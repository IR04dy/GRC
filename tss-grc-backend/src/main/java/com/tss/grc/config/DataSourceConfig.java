package com.tss.grc.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Explicit DataSource configuration for SQL Server 2019 Express Edition.
 *
 * <p>Spring Boot auto-configuration would work without this class, but
 * explicit configuration makes the HikariCP settings visible and ensures
 * the MSSQL JDBC driver properties (Unicode, encrypt, trust cert) are
 * applied correctly in all environments.</p>
 *
 * <p>Connection URL and credentials are read from application.yml /
 * application-{profile}.yml via {@link DataSourceProperties}.</p>
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties props) {
        HikariDataSource ds = props
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();

        // Ensure Arabic/Unicode characters are sent correctly to SQL Server
        ds.addDataSourceProperty("sendStringParametersAsUnicode", "true");

        // Pool name visible in JMX / actuator metrics
        if (ds.getPoolName() == null || ds.getPoolName().isBlank()) {
            ds.setPoolName("TSS-GRC-Pool");
        }

        return ds;
    }
}
