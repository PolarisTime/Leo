package com.leo.erp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Explicit, build-time entry point for Spring Modulith verification.
 *
 * <p>This source is compiled into a temporary tools directory and is never packaged with the application.</p>
 */
public final class ModulithArchitectureVerifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModulithArchitectureVerifier.class);
    private static final String DETECTION_STRATEGY_PROPERTY = "spring.modulith.detection-strategy";
    private static final String EXPLICITLY_ANNOTATED = "explicitly-annotated";

    private ModulithArchitectureVerifier() {
    }

    public static void main(String[] args) {
        System.setProperty(DETECTION_STRATEGY_PROPERTY, EXPLICITLY_ANNOTATED);
        ApplicationModules modules = ApplicationModules.of(LeoApplication.class);
        long declaredModuleCount = modules.stream().count();
        if (declaredModuleCount == 0) {
            throw new IllegalStateException("No explicitly annotated application modules were discovered");
        }

        modules.verify();
        LOGGER.info("Spring Modulith verification passed for {} explicitly annotated modules", declaredModuleCount);
    }
}
