package com.budgetflow.core.context;

import java.util.Optional;

public final class BudgetContextHolder {
    private static final ThreadLocal<BudgetContext> HOLDER = new ThreadLocal<>();

    private BudgetContextHolder() {
    }

    public static void set(BudgetContext context) {
        HOLDER.set(context);
    }

    public static Optional<BudgetContext> current() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static void clear() {
        HOLDER.remove();
    }
}
