package com.example;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL-backed checkouts. Replaces SampleCheckoutRepository.
 * The mock login accepts any name, so forUser() matches what was typed against the user's ID number, first name
 * or full name. A record's borrower is the user's full name.
 */
public class MySqlCheckoutRepository implements CheckoutRepository {

    private static final String SELECT = "SELECT CONCAT(u.First_Name, ' ', u.Last_Name) AS borrower, c.Checkout_Date, c.Due_Date, c.Return_Date, "
            + MySqlCatalogRepository.ITEM_COLUMNS
            + " FROM Check_Out c"
            + " JOIN `User` u ON u.ID_Num = c.UserNum"
            + " JOIN Item i ON i.Barcode = c.ItemBarcode "
            + MySqlCatalogRepository.ITEM_JOINS;

    @Override
    public List<CheckoutRecord> forUser(String username) {
        return load(SELECT + " WHERE (CAST(u.ID_Num AS CHAR) = ? OR LOWER(u.First_Name) = LOWER(?)"
                + " OR LOWER(CONCAT(u.First_Name, ' ', u.Last_Name)) = LOWER(?)) ORDER BY c.Due_Date",
                username.trim(), username.trim(), username.trim());
    }

    @Override
    public List<CheckoutRecord> allRecords() {
        return load(SELECT + " ORDER BY c.Due_Date");
    }

    /** Closes the checkout and frees the item. Both changes happen together or not at all. */
    @Override
    public void markReturned(CheckoutRecord record) {
        if (!record.isOpen()) {
            throw new IllegalStateException("Checkout record has already been returned");
        }
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE Check_Out SET Return_Date = CURDATE(), `Status` = 'Returned' "
                                + "WHERE UserNum IN (SELECT ID_Num FROM `User` WHERE CONCAT(First_Name, ' ', Last_Name) = ?) "
                                + "AND ItemBarcode = ? AND Checkout_Date = ? AND Return_Date IS NULL")) {
                    ps.setString(1, record.borrower);
                    ps.setString(2, record.item.barcode);
                    ps.setDate(3, Date.valueOf(record.checkedOutOn));
                    if (ps.executeUpdate() != 1) {
                        throw new IllegalStateException("That checkout is no longer open. Refresh and try again.");
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE Item SET `Status` = 'Available' WHERE Barcode = ?")) {
                    ps.setString(1, record.item.barcode);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException | java.io.IOException e) {
            throw new IllegalStateException("Could not mark returned: " + e.getMessage(), e);
        }
        // Keep the in-memory object in step with the database.
        record.returnedOn = LocalDate.now();
        record.item.status = ItemStatus.AVAILABLE;
    }

    private List<CheckoutRecord> load(String sql, String... params) {
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<CheckoutRecord> out = new ArrayList<>();
                while (rs.next()) {
                    Date returned = rs.getDate("Return_Date");
                    out.add(new CheckoutRecord(rs.getString("borrower"), MySqlCatalogRepository.mapItem(rs),
                            rs.getDate("Checkout_Date").toLocalDate(), rs.getDate("Due_Date").toLocalDate(),
                            returned == null ? null : returned.toLocalDate()));
                }
                return out;
            }
        } catch (SQLException | java.io.IOException e) {
            throw new IllegalStateException("Checkout query failed: " + e.getMessage(), e);
        }
    }
}
