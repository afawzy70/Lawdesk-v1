package com.lawdesk.platform;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * The product's clock is Kuwait's (owner assumption: Kuwait time; LD-DOC-11 item 15). Bean validation's {@code @PastOrPresent},
 * {@code @FutureOrPresent}, {@code @Past} and {@code @Future} compare a date with "today" on the validator's clock, which is the
 * server's own zone (UTC) by default; between 00:00 and 03:00 in Kuwait that made today's date "in the future" and a case
 * could not be dated with the Kuwait day. The validator now uses this clock.
 */
@Configuration
class ProductClockConfig {

    static final ZoneId KUWAIT = ZoneId.of("Asia/Kuwait");

    @Bean
    Clock productClock() {
        return Clock.system(KUWAIT);
    }

    /** Replaces Boot's default validator so that every request is validated on the product clock. */
    @Bean
    static LocalValidatorFactoryBean defaultValidator(Clock productClock) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setConfigurationInitializer(configuration -> configuration.clockProvider(() -> productClock));
        return validator;
    }
}
