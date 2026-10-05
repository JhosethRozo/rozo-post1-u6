package com.tienda.pedidos;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
public class PedidosServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PedidosServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner initH2IdentityAlias(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute("CREATE ALIAS IF NOT EXISTS IDENTITY AS 'Long id(java.sql.Connection c) throws java.sql.SQLException { java.sql.ResultSet rs = c.createStatement().executeQuery(\"SELECT MAX(id) FROM pedidos\"); return rs.next() ? rs.getLong(1) : 1L; }'");
        };
    }
}
