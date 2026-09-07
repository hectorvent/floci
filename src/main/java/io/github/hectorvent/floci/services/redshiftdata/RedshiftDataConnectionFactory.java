package io.github.hectorvent.floci.services.redshiftdata;

import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

@ApplicationScoped
class RedshiftDataConnectionFactory {

    Connection open(RedshiftDataResourceResolver.DatabaseTarget target) throws SQLException {
        String url = "jdbc:postgresql://" + target.host() + ":" + target.port() + "/" + target.database()
                + "?sslmode=disable";
        Properties props = new Properties();
        props.setProperty("user", target.user());
        props.setProperty("password", target.password());
        props.setProperty("connectTimeout", "5");
        return DriverManager.getConnection(url, props);
    }
}
