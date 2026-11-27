package com.apigensetup;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/** Separate source tree: no scanning of repositories, migrations, schedulers or business controllers. */
@SpringBootConfiguration
@EnableAutoConfiguration(excludeName = {
    "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
    "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
    "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
    "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
    "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"
})
@Import({SetupController.class, SetupSecurity.class})
public class SetupApplication {}
