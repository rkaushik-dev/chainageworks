package io.github.rkaushikdev.chainageworks.domain.subcontract;

import io.github.rkaushikdev.chainageworks.domain.money.Money;
import java.time.LocalDate;

/**
 * The payer's notice of what it thinks an application is worth.
 *
 * @param applicationId the application answered
 * @param servedOn the day it was served
 * @param valued what the payer says the work is worth, gross and cumulative
 */
public record PaymentNotice(String applicationId, LocalDate servedOn, Money valued) {}
