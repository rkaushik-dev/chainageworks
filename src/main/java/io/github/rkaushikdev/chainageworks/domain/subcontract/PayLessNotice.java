package io.github.rkaushikdev.chainageworks.domain.subcontract;

import io.github.rkaushikdev.chainageworks.domain.money.Money;
import java.time.LocalDate;

/**
 * The payer's notice that it intends to pay less than the notified sum.
 *
 * @param applicationId the application answered
 * @param servedOn the day it was served
 * @param amount what the payer intends to pay, as an amount for this application
 * @param reason why
 */
public record PayLessNotice(String applicationId, LocalDate servedOn, Money amount, String reason) {}
