package com.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.time.LocalDate;
import org.junit.Test;

public class SampleCheckoutRepositoryTest {

    @Test
    public void allRecordsIncludesBothBorrowersAndCheckoutStatuses() {
        CheckoutRepository repository = new SampleCheckoutRepository(new SampleCatalogRepository());

        List<CheckoutRecord> records = repository.allRecords();

        assertEquals(7, records.size());
        assertTrue(records.stream().anyMatch(record -> record.borrower.equals("alice")));
        assertTrue(records.stream().anyMatch(record -> record.borrower.equals("bob")));
        assertTrue(records.stream().anyMatch(record -> record.status() == CheckoutStatus.CHECKED_OUT));
        assertTrue(records.stream().anyMatch(record -> record.status() == CheckoutStatus.OVERDUE));
        assertTrue(records.stream().anyMatch(record -> record.status() == CheckoutStatus.RETURNED));
    }

    @Test
    public void allRecordsAreSortedByDueDate() {
        CheckoutRepository repository = new SampleCheckoutRepository(new SampleCatalogRepository());

        List<CheckoutRecord> records = repository.allRecords();

        for (int i = 1; i < records.size(); i++) {
            assertTrue(!records.get(i).dueOn.isBefore(records.get(i - 1).dueOn));
        }
    }

    @Test
    public void markingOverdueCheckoutReturnedUpdatesReturnDateAndItemAvailability() {
        CatalogRepository catalog = new SampleCatalogRepository();
        CheckoutRepository repository = new SampleCheckoutRepository(catalog);
        CheckoutRecord overdue = repository.allRecords().stream()
                .filter(record -> record.item.barcode.equals("BC101"))
                .findFirst()
                .orElseThrow();

        repository.markReturned(overdue);

        assertEquals(CheckoutStatus.RETURNED, overdue.status());
        assertEquals(LocalDate.now(), overdue.returnedOn);
        assertFalse(overdue.isOpen());
        assertEquals(ItemStatus.AVAILABLE, overdue.item.status);
        assertEquals(ItemStatus.AVAILABLE, catalog.findByBarcode("BC101").status);
    }

    @Test(expected = IllegalStateException.class)
    public void cannotMarkReturnedCheckoutReturnedAgain() {
        CheckoutRepository repository = new SampleCheckoutRepository(new SampleCatalogRepository());
        CheckoutRecord returned = repository.allRecords().stream()
                .filter(record -> !record.isOpen())
                .findFirst()
                .orElseThrow();

        repository.markReturned(returned);
    }
}
