package org.enggis.enventx.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVm",
        "minio.access-key=test",
        "minio.secret-key=test",
        "spring.datasource.url=jdbc:h2:mem:ctx;MODE=MySQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class EnventxServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}