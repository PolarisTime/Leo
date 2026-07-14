package com.leo.erp;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;

class LeoApplicationTest {

    @Test
    void shouldDelegateToSpringApplication() {
        try (MockedStatic<SpringApplication> springApplication = Mockito.mockStatic(SpringApplication.class)) {
            String[] args = {"--spring.main.web-application-type=none"};

            LeoApplication.main(args);

            springApplication.verify(() -> SpringApplication.run(LeoApplication.class, args));
        }
    }
}
