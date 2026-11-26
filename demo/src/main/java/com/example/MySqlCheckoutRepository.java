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

    /** Reserve.Status values that mean the reservation is still waiting (lower case, compared ignoring case). */
    private static final java.util.Set<String> ACTIVE_RESERVATION = java.util.Set.of("reserved", "pending", "ready for pickup");

    private static final String SELECT = "SELECT u.ID_Num AS borrower_id, CONCAT(u.First_Name, ' ', u.Last_Name) AS borrower, c.Checkout_Date, c.Due_Date, c.Return_Date, "
            + MySqlCatalogRepository.ITEM_COLUMNS
            + " FROM Check_Out c"
            + " JOIN `User` u ON u.ID_Num = c.UserNum"
            + " JOIN Item i ON i.Barcode = c.ItemBarcode "
            + MySqlCatalogRepository.ITEM_JOINS;

    /** Matches a typed login name against the user's ID number, first name or full name (needs 3 parameters). */
    private static final String USER_MATCH = "(CAST(u.ID_Num AS CHAR) = ? OR LOWER(u.First_Name) = LOWER(?)"
            + " OR LOWER(CONCAT(u.First_Name, ' ', u.Last_Name)) = LOWER(?))";

    @Override
    public List<CheckoutRecord> forUser(String username) {
        return load(SELECT + " WHERE " + USER_MATCH + " ORDER BY c.Due_Date",
                username.trim(), username.trim(), username.trim());
    }

    /** Only rows whose Reserve.Status is 'Reserved'. Alias rv, because ITEM_JOINS already uses r for Record_Info. */
    @Override
    public List<ReservationRecord> reservationsFor(String username) {
        String who = username == null ? "" : username.trim();
        String sql = "SELECT CONCAT(u.First_Name, ' ', u.Last_Name) AS borrower, rv.Reserve_Date, "
                + MySqlCatalogRepository.ITEM_COLUMNS
                + " FROM Reserve rv"
                + " JOIN `User` u ON u.ID_Num = rv.UserNum"
                + " JOIN Item i ON i.Barcode = rv.ItemBarcode "
                + MySqlCatalogRepository.ITEM_JOINS
                + " WHERE " + USER_MATCH + " AND LOWER(TRIM(rv.`Status`)) = 'reserved'"
                + " ORDER BY rv.Reserve_Date";
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, who);
            ps.setString(2, who);
            ps.setString(3, who);
            try (ResultSet rs = ps.executeQuery()) {
                List<ReservationRecord> out = new ArrayList<>();
                while (rs.next()) {
                    Date reservedOn = rs.getDate("Reserve_Date");
                    out.add(new ReservationRecord(rs.getString("borrower"), MySqlCatalogRepository.mapItem(rs),
                            reservedOn == null ? null : reservedOn.toLocalDate()));
                }
                return out;
            }
        } catch (SQLException | java.io.IOException e) {
            throw new IllegalStateException("Reservation query failed: " + e.getMessage(), e);
        }
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
        int userNumOut;
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Who is borrowing? (The mock login lets anyone in, so the account may not exist.)
                Borrower who = findBorrower(c, username);
                int userNum = who.userNum();
                borrowerName = who.name();
                userNumOut = userNum;
                Integer limit = who.limit();

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

                // 5. Checking out a book you had reserved fulfils the reservation, so it stops showing as Reserved.
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE Reserve SET `Status` = 'Fulfilled' WHERE UserNum = ? AND ItemBarcode = ? "
                                + "AND LOWER(TRIM(`Status`)) = 'reserved'")) {
                    ps.setInt(1, userNum);
                    ps.setString(2, item.barcode);
                    ps.executeUpdate();
                }

                // 6. Record the checkout.
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
        return new CheckoutRecord(borrowerName, userNumOut, item, today, due, null);
    }

    /**
     * Reserves a book that someone else has checked out. In one transaction: find the user, lock the item row,
     * confirm it is a checked-out book held by somebody else, and write the Reserve row. If anything fails,
     * nothing is saved.
     * Reserve's primary key is (UserNum, ItemBarcode), so a user has at most one row per item: an old finished
     * reservation is reused instead of inserting a duplicate.
     */
    @Override
    public LocalDate reserve(String username, MediaItem item) {
        if (item.mediaType != MediaType.BOOK) {
            throw new IllegalStateException("Only books can be reserved.");
        }
        LocalDate today = LocalDate.now();
        LocalDate dueBack;
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Who is reserving?
                int userNum = findBorrower(c, username).userNum();

                // 2. Lock the item row (so a return can't slip in between our checks and the insert), make sure
                //    it is a book (equipment, media, etc. have no Book row).
                String dbStatus;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT i.`Status`, (SELECT COUNT(*) FROM Book b WHERE b.Barcode = i.Barcode) "
                                + "FROM Item i WHERE i.Barcode = ? FOR UPDATE")) {
                    ps.setString(1, item.barcode);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("This item no longer exists.");
                        }
                        dbStatus = rs.getString(1);
                        if (rs.getInt(2) == 0) {
                            throw new IllegalStateException("Only books can be reserved.");
                        }
                    }
                }

                // 3. Who has it? Read the open loans (Return_Date IS NULL). The book must not be one of yours.
                Date earliestDue = null;
                List<Integer> holders = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT UserNum, Due_Date FROM Check_Out WHERE ItemBarcode = ? AND Return_Date IS NULL "
                                + "ORDER BY Due_Date")) {
                    ps.setString(1, item.barcode);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            holders.add(rs.getInt(1));
                            if (earliestDue == null) {
                                earliestDue = rs.getDate(2);
                            }
                        }
                    }
                }
                if (holders.contains(userNum)) {
                    throw new IllegalStateException(
                            "You already have this book checked out, so you can't reserve it. (your ID " + userNum + ")");
                }

                // Checked out = there is an open loan (Return_Date IS NULL). Item.Status is not used: it can be
                // stale, and an overdue loan is just an open loan past its due date.
                if (earliestDue == null) {
                    throw new IllegalStateException("Nobody has this book checked out, so it can't be reserved. "
                            + "(No open Check_Out row for " + item.barcode + "; Item.Status is '"
                            + (dbStatus == null ? "" : dbStatus.trim()) + "'; your ID is " + userNum + ")");
                }
                dueBack = earliestDue.toLocalDate();

                // 4. Already reserved by this user? Reserve allows one row per (user, item).
                String existing = null;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT `Status` FROM Reserve WHERE UserNum = ? AND ItemBarcode = ? FOR UPDATE")) {
                    ps.setInt(1, userNum);
                    ps.setString(2, item.barcode);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            existing = rs.getString(1) == null ? "" : rs.getString(1).trim();
                            if (ACTIVE_RESERVATION.contains(existing.toLowerCase())) {
                                throw new IllegalStateException("You have already reserved this book.");
                            }
                        }
                    }
                }

                // 5. Record the reservation (reusing the user's old, finished row if there is one).
                String sql = existing == null
                        ? "INSERT INTO Reserve (Reserve_Date, `Status`, UserNum, ItemBarcode) VALUES (?, 'Reserved', ?, ?)"
                        : "UPDATE Reserve SET Reserve_Date = ?, `Status` = 'Reserved' WHERE UserNum = ? AND ItemBarcode = ?";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setDate(1, Date.valueOf(today));
                    ps.setInt(2, userNum);
                    ps.setString(3, item.barcode);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) { // duplicate primary key: a parallel click got there first
                throw new IllegalStateException("You have already reserved this book.", e);
            }
            throw new IllegalStateException("Could not reserve: " + e.getMessage(), e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read config.properties: " + e.getMessage(), e);
        }
        return dueBack;
    }

    /** A matched User row: what checkOut and reserve both need to know about the person. */
    private record Borrower(int userNum, String name, Integer limit) {
    }

    /**
     * Finds the User for whatever was typed at login (ID number, first name or full name).
     * Throws IllegalStateException if there is no match or more than one.
     */
    private static Borrower findBorrower(Connection c, String username) throws SQLException {
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
                Borrower found = new Borrower(rs.getInt(1), rs.getString(2), (Integer) rs.getObject(3));
                if (rs.next()) {
                    throw new IllegalStateException("More than one account matches \"" + who
                            + "\". Sign in with your ID number instead.");
                }
                return found;
            }
        }
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
                    out.add(new CheckoutRecord(rs.getString("borrower"), rs.getInt("borrower_id"),
                            MySqlCatalogRepository.mapItem(rs),
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
