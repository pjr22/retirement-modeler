package com.retirementmodeler.tax;

import java.math.BigDecimal;

/**
 * One tier of a progressive tax bracket. {@code threshold} is the lower bound of the bracket
 * (inclusive); income above this threshold and below the next tier's threshold is taxed at {@code
 * rate}. The lowest tier always has threshold {@code 0}; the highest tier extends to infinity.
 */
public record BracketTier(BigDecimal threshold, BigDecimal rate) {}
