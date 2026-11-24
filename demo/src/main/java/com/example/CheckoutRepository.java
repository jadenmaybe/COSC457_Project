package com.example;

import java.util.List;

/**
 * Where checkout records come from. The screens only know this interface, so the in-memory sample can later be
 * replaced by a MySQL version. More methods will be added as screens need them
 * (all records for the admin tab, checking an item out, marking it returned).
 */
public interface CheckoutRepository {

    /** Every checkout (open and returned) for one borrower, soonest due date first. */
    List<CheckoutRecord> forUser(String username);
}
