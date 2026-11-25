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

    /**
     * Checks an item out. In one transaction: find the borrower, enforce their borrow limit, claim the item
     * (only if it is still 'Available'), and insert the Check_Out row. If anything fails, nothing is saved.
     */
    @Override
    public CheckoutRecord checkOut(String username, MediaItem item) {
        LocalDate today = LocalDate.now();
        LocalDate due = today.plusDays(LOAN_DAYS);
        String borrowerName;
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Who is borrowing? (The mock login lets anyone in, so the account may not exist.)
                int userNum;
                Integer limit;
                String who = username == null ? "" : username.trim();
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT ID_Num, CONCAT(First_Name, ' ', Last_Name), Limits FROM `User` "
                                + "WHERE CAST(ID_Num AS CHAR) = ? OR LOWER(First_Name) = LOWER(?) "
                                + "OR LOWER(CONCAT(First_Name, ' ', Last_Name)) = LOWER(?)")) {
                    ps.setString(1, who);
                    ps.setString(2, who);
                    ps.setString(3, who);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("No library account matches \"" + who
                                    + "\". Sign in with a name or ID number from the User table.");
                        }
                        userNum = rs.getInt(1);
                        borrowerName = rs.getString(2);
                        limit = (Integer) rs.getObject(3);
                        if (rs.next()) {
                            throw new IllegalStateException("More than one account matches \"" + who
                                    + "\". Sign in with your ID number instead.");
                        }
                    }
                }

                // 2. Borrow limit (User.Limits = most items out at once; NULL = no limit).
                if (limit != null) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT COUNT(*) FROM Check_Out WHERE UserNum = ? AND Return_Date IS NULL")) {
                        ps.setInt(1, userNum);
                        try (ResultSet rs = ps.executeQuery()) {
                            rs.next();
                            if (rs.getInt(1) >= limit) {
                                throw new IllegalStateException(
                                        "You have reached your limit of " + limit + " items checked out.");
                            }
                        }
                    }
                }

                // 3. Which staff member handles it? Check_Out needs a StaffID: use the one who manages this user.
                int staffId;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COALESCE((SELECT MIN(StaffID) FROM User_Managed_By WHERE ID_Num = ?), "
                                + "(SELECT MIN(StaffID) FROM Staff))")) {
                    ps.setInt(1, userNum);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        staffId = rs.getInt(1);
                        if (rs.wasNull()) {
                            throw new IllegalStateException("There is no staff member to record the checkout.");
                        }
                    }
                }

                // 4. Claim the item. The WHERE clause makes this safe if two people click at the same moment:
                //    only one UPDATE can change the row from 'Available'.
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE Item SET `Status` = 'Checked Out' WHERE Barcode = ? AND `Status` = 'Available'")) {
                    ps.setString(1, item.barcode);
                    if (ps.executeUpdate() != 1) {
                        throw new IllegalStateException("This item is no longer available.");
                    }
                }

                // 5. Record the checkout.
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO Check_Out (UserNum, StaffID, ItemBarcode, `Status`, Return_Date, Due_Date, "
                                + "Checkout_Date) VALUES (?, ?, ?, 'Checked Out', NULL, ?, ?)")) {
                    ps.setInt(1, userNum);
                    ps.setInt(2, staffId);
                    ps.setString(3, item.barcode);
                    ps.setDate(4, Date.valueOf(due));
                    ps.setDate(5, Date.valueOf(today));
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) { // duplicate primary key
                throw new IllegalStateException("This account already checked this item out today. "
                        + "Please try again tomorrow.", e);
            }
            throw new IllegalStateException("Could not check out: " + e.getMessage(), e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read config.properties: " + e.getMessage(), e);
        }
        item.status = ItemStatus.CHECKED_OUT;
        return new CheckoutRecord(borrowerName, item, today, due, null);
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
